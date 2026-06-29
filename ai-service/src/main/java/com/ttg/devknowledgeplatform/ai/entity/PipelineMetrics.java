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
}
