package com.ttg.devknowledgeplatform.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistent snapshot of per-request RAG pipeline quality metrics.
 *
 * <p>One row is written per pipeline execution — whether it completes successfully or is
 * aborted by a guard stage. All columns after {@code CREATED_AT} are nullable: a column
 * is {@code NULL} when the corresponding stage did not execute (e.g. {@code CANDIDATE_COUNT}
 * is {@code NULL} when the pipeline aborted before {@code RetrievalStage}).
 *
 * <h3>Design rationale</h3>
 * <p>This entity intentionally does <em>not</em> extend
 * {@link com.ttg.devknowledgeplatform.common.entity.AbstractEntity}. That base class
 * adds audit columns ({@code usrCreation}, {@code usrLastModification}, {@code version})
 * designed for user-managed content — they have no meaning for machine-generated analytics
 * rows. {@code CREATED_AT} alone is sufficient for time-series queries.
 *
 * <h3>Primary use case</h3>
 * <p>Data-driven threshold tuning: query score distributions across real traffic to set
 * {@code evidence-mean-threshold}, {@code anomaly-hard-threshold}, and similar config
 * values instead of guessing.
 */
@Entity
@Table(name = "PIPELINE_METRICS", schema = "product")
@Getter
@Setter
@NoArgsConstructor
public class PipelineMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pipeline_metrics_seq")
    @SequenceGenerator(
            name = "pipeline_metrics_seq",
            sequenceName = "product.PIPELINE_METRICS_SEQ",
            allocationSize = 50)
    @Column(name = "PIPELINE_METRICS_ID")
    private Integer id;

    /** UUID string from {@link com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext#getTraceId()}. */
    @Column(name = "TRACE_ID", length = 36, nullable = false, unique = true)
    private String traceId;

    /** When this record was written; used as the time-series axis for dashboard queries. */
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    /**
     * Simple class name of the pipeline stage that called
     * {@link com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext#abort}; {@code NULL} on success.
     */
    @Column(name = "ABORTED_AT", length = 100)
    private String abortedAt;

    /** Chunks returned by pgvector ANN search; {@code NULL} if aborted before {@code RetrievalStage}. */
    @Column(name = "CANDIDATE_COUNT")
    private Integer candidateCount;

    /** Chunks surviving {@code ScoringStage} + {@code RetrievalAnomalyStage}; {@code NULL} if aborted before those stages. */
    @Column(name = "AFTER_SCORING_COUNT")
    private Integer afterScoringCount;

    /** Chunks selected by MMR for LLM context; {@code NULL} if aborted before {@code MmrStage}. */
    @Column(name = "SELECTED_COUNT")
    private Integer selectedCount;

    /**
     * Arithmetic mean cosine similarity of MMR-selected chunks, computed by {@code EvidenceQualityStage}.
     * {@code NULL} if the pipeline aborted before reaching that stage.
     */
    @Column(name = "EVIDENCE_MEAN_SCORE", precision = 5, scale = 4)
    private Float evidenceMeanScore;

    /**
     * Per-request similarity threshold raised by {@code QueryAnomalyStage} on soft-anomaly detection.
     * {@code NULL} when the query was fully in-domain and the configured default applied.
     */
    @Column(name = "EFFECTIVE_SIM_THRESHOLD", precision = 5, scale = 4)
    private Float effectiveSimThreshold;

    /**
     * Cosine similarity between the generated answer and the centroid of retrieved chunks.
     * {@code NULL} for aborted pipelines or when the quality check was skipped.
     */
    @Column(name = "ANSWER_CONTEXT_SIM", precision = 5, scale = 4)
    private Float answerContextSim;

    /**
     * Cosine similarity between the generated answer and the query embedding.
     * {@code NULL} for aborted pipelines or when the quality check was skipped.
     */
    @Column(name = "ANSWER_QUERY_SIM", precision = 5, scale = 4)
    private Float answerQuerySim;

    /** Whether the answer quality check flagged drift; {@code NULL} for aborted pipelines. */
    @Column(name = "ANSWER_DRIFTED")
    private Boolean answerDrifted;

    // =========================================================================
    // Feature 1 — Stage latencies
    // =========================================================================

    /** Wall-clock ms for {@code ContextualizationStage}; {@code NULL} if aborted before that stage. */
    @Column(name = "CONTEXTUALIZATION_MS")
    private Long contextualizationMs;

    /** Wall-clock ms for {@code EmbeddingStage}; {@code NULL} if aborted before that stage. */
    @Column(name = "EMBEDDING_MS")
    private Long embeddingMs;

    /** Wall-clock ms for {@code RetrievalStage}; {@code NULL} if aborted before that stage. */
    @Column(name = "RETRIEVAL_MS")
    private Long retrievalMs;

    /** Wall-clock ms for the final LLM generation call; {@code NULL} for aborted pipelines. */
    @Column(name = "LLM_GENERATION_MS")
    private Long llmGenerationMs;

    /**
     * Total end-to-end wall-clock time in ms (pipeline stages + LLM generation + quality check).
     * {@code NULL} when not recorded (should always be set on success and abort alike).
     */
    @Column(name = "TOTAL_PIPELINE_MS")
    private Long totalPipelineMs;

    // =========================================================================
    // Feature 2 — Token usage & estimated cost
    // =========================================================================

    /** Prompt tokens consumed by the {@code ContextualizationStage} LLM call. */
    @Column(name = "CONTEXTUALIZATION_INPUT_TOKENS")
    private Integer contextualizationInputTokens;

    /** Completion tokens produced by the {@code ContextualizationStage} LLM call. */
    @Column(name = "CONTEXTUALIZATION_OUTPUT_TOKENS")
    private Integer contextualizationOutputTokens;

    /** Input tokens consumed by the {@code EmbeddingStage} query embedding call. */
    @Column(name = "EMBEDDING_TOKENS")
    private Integer embeddingTokens;

    /** Input tokens consumed by the answer quality check embedding call. */
    @Column(name = "QUALITY_EMBEDDING_TOKENS")
    private Integer qualityEmbeddingTokens;

    /** Prompt tokens consumed by the final LLM generation call. */
    @Column(name = "GENERATION_INPUT_TOKENS")
    private Integer generationInputTokens;

    /** Completion tokens produced by the final LLM generation call. */
    @Column(name = "GENERATION_OUTPUT_TOKENS")
    private Integer generationOutputTokens;

    /**
     * Estimated USD cost for this request, computed from token counts and model pricing
     * at record-write time. Stored for dashboard queries; recomputable from raw token columns.
     * {@code NULL} when all token counts are zero (aborted pipeline with no LLM calls).
     */
    @Column(name = "ESTIMATED_COST_USD", precision = 12, scale = 8)
    private BigDecimal estimatedCostUsd;

    // =========================================================================
    // Feature 3 — User attribution
    // =========================================================================

    /**
     * ID of the authenticated user who triggered this pipeline execution.
     * {@code NULL} for anonymous calls or internal non-HTTP invocations.
     * Intentionally not a foreign key — this is an analytics table; user deletion
     * must not cascade into historical cost records.
     */
    @Column(name = "USER_ID")
    private Integer userId;
}
