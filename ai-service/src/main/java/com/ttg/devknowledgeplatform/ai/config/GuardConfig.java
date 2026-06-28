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

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for all pipeline guard stages and quality checks.
 *
 * <p>Bound from the {@code app.ai.guards} prefix. Groups anomaly detection thresholds,
 * evidence quality guards, answer drift detection, conversation topic shift detection,
 * and prompt injection detection into a single cohesive namespace.
 *
 * <p>Each sub-group holds both the numeric threshold and the user-facing fallback message
 * returned when the guard fires — keeping threshold and message together makes it obvious
 * which message belongs to which condition.
 */
@ConfigurationProperties(prefix = "app.ai.guards")
@Validated
@Getter
@Setter
public class GuardConfig {

    // ── Query anomaly detection ───────────────────────────────────────────────────

    /**
     * Cosine similarity floor below which a query is considered completely outside the
     * platform's knowledge domain. {@code QueryAnomalyStage} aborts the pipeline and
     * returns {@link #outOfScopeAnswer} — no retrieval or LLM call is made.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalyHardThreshold = 0.20f;

    /**
     * Cosine similarity below which a query is treated as a soft anomaly.
     * The pipeline continues but {@code QueryAnomalyStage} applies
     * {@link #anomalySoftSimilarityThreshold} as the per-chunk retrieval floor.
     * Must be greater than {@link #anomalyHardThreshold}.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalySoftThreshold = 0.40f;

    /**
     * Stricter retrieval similarity threshold applied for soft anomaly queries,
     * replacing the default {@link RetrievalConfig#similarityThreshold} for that request only.
     * Should be higher than the default threshold (0.75).
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float anomalySoftSimilarityThreshold = 0.82f;

    /** User-facing message returned when a hard anomaly aborts the pipeline. */
    @NotBlank
    private String outOfScopeAnswer;

    // ── Evidence quality ──────────────────────────────────────────────────────────

    /**
     * Minimum number of MMR-selected chunks required before the pipeline proceeds to the LLM.
     * A single chunk is too thin a base — the LLM may hallucinate to fill gaps.
     */
    @Positive
    private int evidenceMinChunks = 2;

    /**
     * Minimum arithmetic mean of similarity scores across all MMR-selected chunks.
     * Should be set above {@link RetrievalConfig#similarityThreshold} (the per-chunk floor)
     * but tuned to the typical mean for well-matched queries in your corpus.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float evidenceMeanThreshold = 0.82f;

    /**
     * User-facing message returned by {@code EvidenceQualityStage} when the MMR-selected
     * chunks fail the mean score or minimum count guards.
     * Distinct from the "no information" abort in {@code ScoringStage} — this message
     * signals that the topic exists but is not covered well enough.
     */
    @NotBlank
    private String evidenceInsufficientAnswer;

    // ── Answer quality (post-generation drift detection) ──────────────────────────

    /**
     * Minimum cosine similarity between the generated answer and the normalised centroid
     * of MMR-selected context chunks. Answers below this threshold are logged as potential
     * hallucinations. Softer than {@link RetrievalConfig#similarityThreshold} because
     * generated prose is more verbose and dilutes the topical signal.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float answerContextSimilarityThreshold = 0.70f;

    /**
     * Minimum cosine similarity between the generated answer and the query embedding.
     * Answers below this threshold are logged as potentially off-topic — the LLM answered
     * a different question even if the answer is internally coherent.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float answerQuerySimilarityThreshold = 0.65f;

    // ── Conversation topic shift ───────────────────────────────────────────────────

    /**
     * Cosine similarity floor below which a new question is considered a sudden topic shift.
     * When triggered, {@code ConversationTopicGuardService} strips recent turns so
     * {@code ContextualizationStage} treats the question as standalone.
     * Set to {@code 0.0} to disable. Calibrate from real traffic logs.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float conversationTopicShiftThreshold = 0.35f;

    // ── Prompt injection detection ────────────────────────────────────────────────

    /**
     * Nested configuration for the two-layer injection guard used by {@code PromptGuardStage}
     * (user input) and {@code RetrievedContentGuardStage} (corpus chunks).
     */
    @Valid
    private InjectionDetectionProperties injectionDetection = new InjectionDetectionProperties();

    /**
     * Configuration for the three-layer prompt injection detection guard.
     *
     * <h3>Layer 1 — Length guard</h3>
     * <p>Rejects queries exceeding {@link #maxQueryLength}. Zero latency.
     *
     * <h3>Layer 2 — Lexical matching</h3>
     * <p>Case-insensitive substring match against {@link #patterns}. Zero latency.
     *
     * <h3>Layer 3 — Semantic similarity</h3>
     * <p>Embeds the query and computes max cosine similarity against pre-embedded
     * {@link #prototypes}. Cost: one {@code EmbeddingService.embed()} call — only made
     * when layers 1 and 2 both pass.
     */
    @Getter
    @Setter
    public static class InjectionDetectionProperties {

        /** Maximum allowed query length in characters. Oversized inputs are rejected before any pattern check. */
        @Positive
        private int maxQueryLength = 1000;

        /**
         * Known injection phrases for the lexical layer (Option A).
         * Case-insensitive substring match against the raw user query.
         */
        private List<String> patterns = new ArrayList<>();

        /**
         * Canonical injection example sentences for the semantic layer (Option B).
         * Each is embedded once at startup; queries matching at or above
         * {@link #similarityThreshold} are rejected.
         */
        private List<String> prototypes = new ArrayList<>();

        /**
         * Cosine similarity threshold for the semantic layer.
         * A conservative default of {@code 0.80} limits false positives on legitimate
         * technical questions that share vocabulary with injection phrases.
         */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private float similarityThreshold = 0.80f;

        /**
         * User-facing rejection message. Intentionally vague — does not reveal which
         * layer fired or what pattern matched to avoid helping an attacker refine their payload.
         */
        private String rejectionMessage = "Your question could not be processed. Please rephrase and try again.";
    }
}
