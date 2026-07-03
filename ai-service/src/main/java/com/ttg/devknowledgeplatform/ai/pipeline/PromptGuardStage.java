package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.GuardConfig;
import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
 * <p>Prototype embeddings are computed once via {@link EmbeddingService#embedBatch} during
 * {@link #init()}. If the embedding API is unavailable at startup (e.g. no OPENAI_API_KEY),
 * the semantic layer is disabled with a {@code WARN} log and the application continues with
 * layers 1–2 active. Once the API is available on the next restart, the semantic layer
 * automatically re-enables.
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

    /** L2-normalised prototype embeddings; empty list when startup embedding failed or no prototypes configured. */
    private List<float[]> prototypeEmbeddings = Collections.emptyList();

    /**
     * Embeds all configured injection prototype phrases once at startup.
     *
     * <p>Using {@link EmbeddingService#embedBatch} batches all prototypes into a single API
     * call — typically one HTTP round trip regardless of prototype count. The resulting vectors
     * are L2-normalised by the OpenAI API, so {@link VectorUtils#dotProduct} gives exact cosine
     * similarity at request time without an extra normalisation step.
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
        try {
            prototypeEmbeddings = embeddingService.embedBatch(prototypes);
            log.info("PromptGuardStage: embedded {} injection prototypes — semantic layer active",
                    prototypeEmbeddings.size());
        } catch (Exception e) {
            log.warn("PromptGuardStage: failed to embed injection prototypes at startup — "
                    + "semantic layer disabled; lexical guard (layers 1-2) remains active. Cause: {}",
                    e.getMessage());
        }
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
