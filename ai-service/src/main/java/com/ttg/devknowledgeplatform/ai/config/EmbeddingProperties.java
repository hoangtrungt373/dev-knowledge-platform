package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.ttg.devknowledgeplatform.ai.dto.RagFilter;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.ai.embedding")
@Validated
@Getter
@Setter
public class EmbeddingProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String model = "text-embedding-3-small";

    @Positive
    private int dimensions = 1536;

    /** Target token count per chunk. 1 token ≈ 4 characters. */
    @Positive
    private int chunkSize = 512;

    /** Token overlap between consecutive chunks to preserve context at boundaries. */
    @Positive
    private int chunkOverlap = 100;

    // --- Chat / generation ---

    @NotBlank
    private String chatModel = "gpt-4o-mini";

    @Positive
    private int maxTokens = 1024;

    private double temperature = 0.7;

    @Positive
    private int maxRetries = 3;

    // --- RAG ---

    @Positive
    private int topK = 5;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private float similarityThreshold = 0.75f;

    /**
     * Multiplier applied to {@code topK} when a {@link RagFilter}
     * is active. Fetching more candidates than needed ensures the post-filter pool remains large
     * enough to yield {@code topK} results after filtering, compensating for HNSW's inability
     * to perform efficient filtered approximate nearest-neighbour search.
     */
    @Positive
    private int oversampleFactor = 3;

    /**
     * Lambda (λ) for Maximal Marginal Relevance re-ranking.
     *
     * <p>Controls the relevance/diversity trade-off when selecting the final {@code topK} chunks:
     * <ul>
     *   <li>{@code 1.0} — pure relevance; equivalent to sorting by cosine similarity (no diversity)</li>
     *   <li>{@code 0.5} — equal weight between relevance and diversity (default)</li>
     *   <li>{@code 0.0} — pure diversity; ignores relevance scores entirely</li>
     * </ul>
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float mmrLambda = 0.5f;

    // --- Prompt label strings ---

    /**
     * Label prepended to the rolling summary when building the contextualization rewrite prompt.
     * Placed before the summary text so the LLM understands it is historical context.
     */
    @NotBlank
    private String contextSummaryLabel = "Summary of earlier conversation:\n";

    /**
     * Label prepended to the current user question when building the contextualization rewrite prompt.
     * Signals to the LLM that what follows is the ambiguous follow-up to rewrite.
     */
    @NotBlank
    private String contextFollowUpLabel = "\nFollow-up: ";

    /**
     * Label used as the user message that injects the rolling summary into the LLM message list.
     * Placed before the summary text so the model treats it as prior conversation context.
     */
    @NotBlank
    private String historySummaryLabel = "Earlier conversation summary:\n";

    /**
     * Synthetic AI acknowledgement message that follows the injected summary in the message list.
     * Closes the synthetic User/Assistant exchange used to inject compressed history.
     */
    @NotBlank
    private String historySummaryAck = "Understood. I will keep this context in mind while answering.";

    /**
     * Label prepended to the previous summary text in the compression prompt.
     * Instructs the LLM to extend rather than rewrite the existing summary.
     */
    @NotBlank
    private String compressionPreviousSummaryLabel = "\n\nPrevious summary (extend this, do not repeat it verbatim):\n";

    /**
     * Label that separates the previous summary from the new turns in the compression prompt.
     * Signals to the LLM where the new content to compress begins.
     */
    @NotBlank
    private String compressionTurnsLabel = "\n\nConversation turns to compress:\n";

    // --- System prompts ---

    @NotBlank
    private String systemPrompt;

    /** System prompt prefix applied when the query is scoped exclusively to {@code ARTICLE} content. */
    @NotBlank
    private String systemPromptArticle;

    /** System prompt prefix applied when the query is scoped exclusively to {@code INTERVIEW_QUESTION} content. */
    @NotBlank
    private String systemPromptInterviewQuestion;

    /** System prompt prefix applied when the query is scoped exclusively to {@code BLOG_POST} content. */
    @NotBlank
    private String systemPromptBlogPost;

    /**
     * How often {@code CorpusStatisticsService} recomputes and persists corpus centroids.
     * Expressed as an ISO-8601 duration string (e.g. {@code PT6H} = every 6 hours).
     * The default of 6 hours is a good balance: content is curated and changes infrequently,
     * so recomputing more often wastes DB resources; recomputing less often risks stale centroids
     * after a large content import.
     */
    private String centroidRefreshInterval = "PT6H";

    /**
     * Minimum mean cosine similarity between a document's chunk embeddings and the corpus
     * centroid for the document to be considered good quality at indexing time.
     *
     * <p>Computed by {@code IndexingQualityServiceImpl} after all chunks are embedded.
     * Documents scoring below this threshold have their {@code ContentItem.qualityScore} recorded
     * but are still stored — the score is surfaced for admin review rather than silently discarded.
     * Set to {@code 0.0} to disable the check without a code change.
     *
     * <p>Should be calibrated against real data: log {@code qualityScore} values for known-good
     * and known-bad documents, then set the threshold at the distribution boundary.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float indexingCoherenceThreshold = 0.35f;

    /**
     * User-facing message returned by {@code EvidenceQualityStage} when the final MMR-selected
     * chunks fail the mean score or minimum count guards.
     *
     * <p>Distinct from {@link RagPipelineContext#NO_CONTEXT_ANSWER} (used by {@code ScoringStage}
     * when <em>zero</em> chunks survive the absolute threshold) — this message applies when chunks
     * were found but are collectively too weak to support a reliable answer. A more specific
     * message helps the user understand that the topic exists in the corpus but is not covered
     * well enough, rather than implying it does not exist at all.
     */
    @NotBlank
    private String evidenceInsufficientAnswer;

    /**
     * Minimum number of chunks that must survive MMR selection for the pipeline to continue.
     * Fewer chunks than this threshold means the corpus coverage is too thin to support a
     * reliable answer — the LLM would likely hallucinate or over-extrapolate from a single source.
     * {@code EvidenceQualityStage} aborts the pipeline if this condition is not met.
     */
    @Positive
    private int evidenceMinChunks = 2;

    /**
     * Minimum arithmetic mean of similarity scores across all MMR-selected chunks.
     * Even when individual chunks clear the absolute {@link #similarityThreshold} floor,
     * a low collective mean indicates borderline evidence that the LLM cannot reliably use.
     * {@code EvidenceQualityStage} aborts the pipeline if the mean falls below this value.
     *
     * <p>Should be set above {@link #similarityThreshold} (the per-chunk floor) but tuned
     * to the typical mean observed for well-matched queries in your corpus.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float evidenceMeanThreshold = 0.82f;

    /**
     * Minimum score gap between consecutive chunks (sorted descending) that triggers outlier
     * pruning in {@code RetrievalAnomalyStage}.
     *
     * <p>When the largest consecutive gap in the scored chunk list exceeds this value, every
     * chunk below the gap is discarded before MMR selection runs. A gap of {@code 0.15} means
     * a drop of 15 percentage points in a single step is treated as a categorical relevance
     * boundary, not a gradual decrease. Set to {@code 0.0} to disable pruning entirely.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float retrievalOutlierGapThreshold = 0.15f;

    /**
     * Cosine similarity floor below which a query is considered completely outside the
     * platform's knowledge domain. {@code QueryAnomalyStage} aborts the pipeline and
     * returns an out-of-scope message — no retrieval or LLM call is made.
     *
     * <p>Measured against the L2-normalised corpus centroid, so the range is {@code [0, 1]}.
     * A value around {@code 0.20} rejects only clearly unrelated queries (e.g. cooking recipes
     * asked against a software engineering corpus).
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalyHardThreshold = 0.20f;

    /**
     * Cosine similarity below which a query is treated as a soft anomaly — potentially
     * related but marginal. The pipeline continues but {@code QueryAnomalyStage} applies a
     * stricter retrieval similarity threshold ({@link #anomalySoftSimilarityThreshold}) to
     * reduce the risk of hallucination on borderline topics.
     *
     * <p>Must be greater than {@link #anomalyHardThreshold}. Queries with similarity
     * at or above this value are treated as fully in-domain.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalySoftThreshold = 0.40f;

    /**
     * Retrieval similarity threshold applied when a soft anomaly is detected.
     * Replaces the default {@link #similarityThreshold} for that request only,
     * requiring retrieved chunks to be a closer match before they pass into the LLM context.
     *
     * <p>Should be higher than {@link #similarityThreshold} (default 0.75).
     * A value of {@code 0.82} allows 7 pp extra headroom before a chunk is accepted.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalySoftSimilarityThreshold = 0.82f;

    /**
     * Minimum cosine similarity between the generated answer embedding and the normalised
     * centroid of the MMR-selected context chunks. Answers below this threshold are logged
     * as potential hallucinations — the LLM may have departed from the retrieved material
     * and drawn on training data instead.
     *
     * <p>Softer than the per-chunk retrieval threshold ({@link #similarityThreshold}) because
     * generated text is more verbose: bridging prose, hedges, and transitional sentences
     * naturally dilute the pure topical signal. Tune downward if legitimate answers are
     * flagged; tune upward if hallucinations pass undetected.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float answerContextSimilarityThreshold = 0.70f;

    /**
     * Minimum cosine similarity between the generated answer embedding and the query embedding.
     * Answers below this threshold are logged as potentially off-topic — the LLM answered a
     * different question than was asked, even if the answer text is internally coherent.
     *
     * <p>Complements {@link #answerContextSimilarityThreshold}: context similarity catches
     * hallucination (answer not grounded in retrieved chunks); query similarity catches
     * topic drift (answer grounded but addresses the wrong question).
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float answerQuerySimilarityThreshold = 0.65f;

    /**
     * Prompt injection detection configuration.
     *
     * <p>Groups all settings for {@code PromptGuardStage}: lexical patterns, semantic
     * prototype phrases, the similarity threshold, and the rejection message. Nested
     * so that all injection-detection settings appear under a single YAML key rather
     * than scattered among the flat {@code EmbeddingProperties} fields.
     */
    @Valid
    private InjectionDetectionProperties injectionDetection = new InjectionDetectionProperties();

    /**
     * Prompt sent to the LLM to both resolve pronoun references and enrich the raw user question
     * into a structured four-part form (CONTEXT / TASK / CONSTRAINTS / OUTPUT_FORMAT).
     * The LLM response must contain five labelled lines (STANDALONE, CONTEXT, TASK,
     * CONSTRAINTS, OUTPUT_FORMAT); see {@code ContextualizationStage} for the parse logic.
     */
    @NotBlank
    private String inputEnrichmentPrompt;

    /**
     * User-facing message returned by {@code QueryAnomalyStage} when a query is classified
     * as a hard anomaly (cosine similarity to corpus centroid below {@link #anomalyHardThreshold}).
     * Externalised so operators can tailor the wording to the platform's audience without a
     * code change or redeploy.
     */
    @NotBlank
    private String outOfScopeAnswer;

    /**
     * Prompt prefix sent to the LLM when compressing old conversation turns into a rolling summary.
     * The implementation appends the previous summary (if any) and the turns to compress after this prefix.
     */
    @NotBlank
    private String summarisationPrompt;

    // -------------------------------------------------------------------------
    // Nested configuration classes
    // -------------------------------------------------------------------------

    /**
     * Configuration for the two-layer prompt injection detection guard in {@code PromptGuardStage}.
     *
     * <h3>Option A — Lexical (zero latency)</h3>
     * <p>Checks the raw user query against {@link #patterns} using case-insensitive substring
     * matching. Fires instantly, with no network call. Effective against known, unmodified
     * injection phrases.
     *
     * <h3>Option B — Semantic (one embedding call)</h3>
     * <p>At startup, each phrase in {@link #prototypes} is embedded once via
     * {@code EmbeddingService.embedBatch()}. At request time, the raw user query is embedded and
     * its max cosine similarity to all prototypes is compared against {@link #similarityThreshold}.
     * Catches paraphrases and novel phrasings that evade the lexical layer.
     *
     * <p>Option A always runs first. Option B's embedding call is only made when the query passes
     * the lexical check — so caught injections never incur an embedding cost.
     */
    @Getter
    @Setter
    public static class InjectionDetectionProperties {

        /**
         * Maximum allowed query length in characters.
         * Oversized inputs are a common prompt-stuffing vector; an extremely long query
         * may attempt to bury injection instructions inside legitimate-looking text.
         * Queries exceeding this length are rejected before any pattern check.
         */
        @Positive
        private int maxQueryLength = 1000;

        /**
         * Known injection phrases for Option A (lexical layer).
         * Each entry is a case-insensitive substring pattern checked against the raw user query.
         * Configure via {@code injection-detection.patterns} in YAML.
         *
         * <p>When defined in YAML, the YAML list replaces this default (Spring Boot list
         * properties are not merged). Operators must include all desired phrases explicitly.
         */
        private List<String> patterns = new ArrayList<>();

        /**
         * Canonical injection example sentences for Option B (semantic layer).
         * Each entry is embedded once at startup; at request time, the query is rejected when
         * its cosine similarity to any prototype equals or exceeds {@link #similarityThreshold}.
         * Configure via {@code injection-detection.prototypes} in YAML.
         *
         * <p>Prototypes should cover diverse phrasings of the same intent — "ignore previous
         * instructions", "you are now", "reveal your system prompt" — so the semantic space
         * around injection attempts is densely covered. More prototypes increase recall with
         * negligible runtime cost (dot products are cheap compared to the embedding call itself).
         */
        private List<String> prototypes = new ArrayList<>();

        /**
         * Cosine similarity threshold for the semantic layer.
         * Queries whose max similarity to any prototype equals or exceeds this value are rejected.
         *
         * <p>A conservative default of {@code 0.80} limits false positives on legitimate
         * technical questions that share vocabulary with injection phrases (e.g.
         * "how do I ignore a Spring configuration?"). Tune downward if paraphrased injections
         * are bypassing the guard; tune upward if legitimate queries are being rejected.
         */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private float similarityThreshold = 0.80f;

        /**
         * User-facing message returned when any injection guard fires.
         *
         * <p>Intentionally vague — does not reveal which layer triggered (lexical vs semantic)
         * or which pattern matched. Giving precise feedback would help an attacker refine
         * their payload. Operators may customise the wording without changing the non-disclosure intent.
         */
        private String rejectionMessage =
                "Your question could not be processed. Please rephrase and try again.";
    }
}
