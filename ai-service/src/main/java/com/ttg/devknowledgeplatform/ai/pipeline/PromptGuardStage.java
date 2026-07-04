package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.GuardConfig;
import com.ttg.devknowledgeplatform.ai.config.ModelConfig;
import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import com.ttg.devknowledgeplatform.common.service.SysParamService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * First pipeline stage — guards against prompt-injection attacks before any LLM call is made.
 *
 * <p>Operates exclusively on {@link RagPipelineContext#getOriginalQuestion()} — the raw,
 * unmodified user input — and must run before {@code ContextualizationStage}, which would
 * otherwise forward an injected payload directly to the LLM. Three layers execute in
 * increasing cost order; each layer aborts immediately on detection, so the next layer's
 * cost is only paid when the previous one passes:
 *
 * <ol>
 *   <li><strong>Layer 1 — Length guard</strong>: rejects queries exceeding
 *       {@code injection-detection.max-query-length}. Oversized inputs are a common
 *       prompt-stuffing vector — a very long query may attempt to bury injection instructions
 *       inside legitimate-looking text. Zero latency: a simple {@code String.length()} check.</li>
 *   <li><strong>Layer 2 — Lexical matching (Option A)</strong>: case-insensitive substring match
 *       of the raw query against a configurable list of known injection phrases
 *       ({@code injection-detection.patterns}). Effective against direct, unparaphrased attacks.
 *       Zero latency: string operations only.</li>
 *   <li><strong>Layer 3 — Semantic similarity (Option B)</strong>: embeds the query and computes
 *       its max cosine similarity against a set of pre-embedded prototype injection phrases
 *       ({@code injection-detection.prototypes}). Catches paraphrases and novel phrasings that
 *       bypass the lexical layer. Cost: one {@code EmbeddingService.embed()} call — only made
 *       when layers 1 and 2 both pass.</li>
 * </ol>
 *
 * <h3>Startup behaviour</h3>
 * <p>Prototype embeddings are computed once via {@link EmbeddingService#embedBatch} and then
 * cached in {@code SYS_PARAM} under {@link ParamKey#PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS} — see
 * {@link #init()}. Every subsequent restart reads the cached vectors instead of re-embedding,
 * as long as neither the prototype list ({@code injection-detection.prototypes}) nor the
 * embedding model ({@code app.ai.embedding-model.model}) has changed since the value was
 * cached; a SHA-256 fingerprint of both is stored alongside the vectors and checked on load,
 * so an operator editing either config value transparently invalidates the cache without any
 * manual database cleanup. If the embedding API is unavailable at startup (e.g. no
 * OPENAI_API_KEY) and no valid cache exists, the semantic layer is disabled with a
 * {@code WARN} log and the application continues with layers 1–2 active. Once the API is
 * available on a later restart, the semantic layer automatically re-enables and populates
 * the cache.
 *
 * <h3>Rejection message design</h3>
 * <p>The user-facing rejection message is intentionally vague — it reveals neither which
 * layer fired nor what pattern matched. Specificity would let an attacker iteratively refine
 * their payload until it slips through. Operators may customise the wording via
 * {@code injection-detection.rejection-message} while preserving non-disclosure.
 *
 * <h3>False positive mitigation</h3>
 * <p>The semantic threshold (default {@code 0.80}) is deliberately conservative — similarity
 * to a prototype must be very high before the query is blocked. Legitimate technical questions
 * that share vocabulary with injection phrases (e.g. "how do I ignore a Spring security
 * configuration?") typically score {@code 0.50–0.70} against injection prototypes and pass
 * cleanly. Adjust {@code similarity-threshold} in YAML if false positives occur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptGuardStage implements RagPipelineStage {

    private final GuardConfig guards;
    private final EmbeddingService embeddingService;
    private final SysParamService sysParamService;
    private final ModelConfig modelConfig;

    /** L2-normalised prototype embeddings; empty list when startup embedding failed or no prototypes configured. */
    private List<float[]> prototypeEmbeddings = Collections.emptyList();

    /**
     * Loads or computes embeddings for all configured injection prototype phrases once at startup.
     *
     * <p>A SHA-256 fingerprint of the embedding model id plus the prototype list is checked
     * against the cached value in {@code SYS_PARAM} first ({@link #loadFromCache}); on a hit,
     * no embedding API call is made at all. On a miss (first run, or the config changed),
     * {@link EmbeddingService#embedBatch} batches all prototypes into a single API call — typically
     * one HTTP round trip regardless of prototype count — and the result is persisted for the
     * next restart. The resulting vectors are L2-normalised by the OpenAI API, so
     * {@link VectorUtils#dotProduct} gives exact cosine similarity at request time without an
     * extra normalisation step.
     *
     * <p>Failures are caught and logged rather than propagated — a startup embedding failure
     * must not prevent the application from starting, since layers 1 and 2 remain active.
     */
    @PostConstruct
    public void init() {
        List<String> prototypes = guards.getInjectionDetection().getPrototypes();
        if (prototypes.isEmpty()) {
            log.info("PromptGuardStage: no injection prototypes configured — semantic layer (Option B) disabled");
            return;
        }

        String fingerprint = computeFingerprint(prototypes);
        Optional<List<float[]>> cached = loadFromCache(fingerprint);
        if (cached.isPresent()) {
            prototypeEmbeddings = cached.get();
            log.info("PromptGuardStage: loaded {} injection prototype embeddings from cache — semantic layer active",
                    prototypeEmbeddings.size());
            return;
        }

        try {
            prototypeEmbeddings = embeddingService.embedBatch(prototypes);
            sysParamService.upsert(ParamKey.PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS,
                    serialize(fingerprint, prototypeEmbeddings));
            log.info("PromptGuardStage: embedded {} injection prototypes — semantic layer active",
                    prototypeEmbeddings.size());
        } catch (Exception e) {
            log.warn("PromptGuardStage: failed to embed injection prototypes at startup — "
                    + "semantic layer disabled; lexical guard (layers 1-2) remains active. Cause: {}",
                    e.getMessage());
        }
    }

    /**
     * Computes a stable fingerprint of everything that determines whether cached prototype
     * embeddings are still valid: the embedding model id (a model change means a different
     * vector space, incompatible with previously cached vectors) and the prototype text itself
     * (an operator edit to the list must invalidate the cache).
     */
    private String computeFingerprint(List<String> prototypes) {
        String material = modelConfig.getModel() + "|" + String.join("|", prototypes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JDK distribution (JLS-mandated algorithm).
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", e);
        }
    }

    /**
     * Loads cached prototype embeddings from {@code SYS_PARAM}, returning empty when no cache
     * exists yet or the stored fingerprint no longer matches {@code expectedFingerprint} —
     * either case falls through to re-embedding in {@link #init()}.
     */
    private Optional<List<float[]>> loadFromCache(String expectedFingerprint) {
        return sysParamService.getValue(ParamKey.PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS)
                .flatMap(value -> deserialize(value, expectedFingerprint));
    }

    /**
     * Parses the cached value format: first line is the fingerprint, each remaining line is one
     * pgvector-notation vector. Returns empty if the fingerprint doesn't match (stale cache).
     */
    private Optional<List<float[]>> deserialize(String value, String expectedFingerprint) {
        String[] lines = value.split("\n", -1);
        if (lines.length == 0 || !lines[0].equals(expectedFingerprint)) {
            return Optional.empty();
        }
        List<float[]> vectors = new ArrayList<>(lines.length - 1);
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                vectors.add(VectorUtils.parseVector(lines[i]));
            }
        }
        return vectors.isEmpty() ? Optional.empty() : Optional.of(vectors);
    }

    /**
     * Serializes the fingerprint and embeddings into the {@code SYS_PARAM} text format described
     * in {@link #deserialize}.
     */
    private String serialize(String fingerprint, List<float[]> embeddings) {
        StringBuilder sb = new StringBuilder(fingerprint);
        for (float[] vector : embeddings) {
            sb.append('\n').append(VectorUtils.toVectorString(vector));
        }
        return sb.toString();
    }

    /**
     * Runs the three-layer injection guard against the raw user query.
     *
     * <p>The guard is deliberately asymmetric in cost: layers 1–2 are free; layer 3 only runs
     * when they both pass, so the embedding cost is avoided for every caught injection. In
     * practice, direct attacks are caught by the lexical layer and the semantic layer handles
     * the more sophisticated attempts that require an embedding call anyway.
     *
     * @param ctx the mutable pipeline context; aborted with {@code rejectionMessage} if any
     *            layer detects an injection attempt
     */
    @Override
    public void process(RagPipelineContext ctx) {
        String query = ctx.getOriginalQuestion();
        GuardConfig.InjectionDetectionProperties config = guards.getInjectionDetection();

        // Layer 1: length guard
        if (query.length() > config.getMaxQueryLength()) {
            log.warn("PromptGuardStage: query rejected — length {} exceeds configured max {}",
                    query.length(), config.getMaxQueryLength());
            ctx.abort(config.getRejectionMessage());
            return;
        }

        // Layer 2: lexical pattern matching (Option A)
        String lower = query.toLowerCase();
        for (String pattern : config.getPatterns()) {
            if (lower.contains(pattern.toLowerCase())) {
                log.warn("PromptGuardStage: query rejected — matched lexical injection pattern");
                ctx.abort(config.getRejectionMessage());
                return;
            }
        }

        // Layer 3: semantic similarity to injection prototypes (Option B)
        if (!prototypeEmbeddings.isEmpty()) {
            EmbedResult result = embeddingService.embed(query);
            float maxSimilarity = computeMaxSimilarity(result.vector(), prototypeEmbeddings);
            if (maxSimilarity >= config.getSimilarityThreshold()) {
                log.warn("PromptGuardStage: query rejected — semantic similarity {} to injection prototypes",
                        maxSimilarity);
                ctx.abort(config.getRejectionMessage());
            }
        }
    }

    /**
     * Returns the maximum cosine similarity between a query embedding and the list of prototype
     * embeddings. Both the query and the prototypes are L2-normalised (OpenAI guarantees this),
     * so {@link VectorUtils#dotProduct} yields exact cosine similarity — no further normalisation
     * is needed.
     *
     * <p>Scanning all prototypes is O(n × d) where n is the prototype count (typically &lt;20)
     * and d is the embedding dimension (1536). This is negligible compared to the embedding call
     * that precedes it.
     *
     * @param query      L2-normalised query embedding
     * @param prototypes list of L2-normalised prototype embeddings
     * @return maximum cosine similarity across all prototypes, in {@code [0, 1]}
     */
    private float computeMaxSimilarity(float[] query, List<float[]> prototypes) {
        float max = 0f;
        for (float[] prototype : prototypes) {
            float similarity = VectorUtils.dotProduct(query, prototype);
            if (similarity > max) {
                max = similarity;
            }
        }
        return max;
    }
}
