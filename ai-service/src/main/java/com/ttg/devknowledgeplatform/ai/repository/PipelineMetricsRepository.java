package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummaryProjection;
import com.ttg.devknowledgeplatform.ai.entity.PipelineMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Spring Data repository for {@link PipelineMetrics} analytics rows.
 *
 * <p>Writes are the primary operation (one INSERT per RAG request). The summary
 * query uses a native SQL {@code @Query} because {@code percentile_cont … WITHIN GROUP}
 * is a PostgreSQL ordered-set aggregate function with no JPQL equivalent.
 */
public interface PipelineMetricsRepository extends JpaRepository<PipelineMetrics, Integer> {

    /**
     * Returns a single-row aggregate over all executions whose {@code CREATED_AT} falls
     * at or after {@code since}.
     *
     * <h3>SQL design notes</h3>
     * <ul>
     *   <li>{@code percentile_cont(p) WITHIN GROUP (ORDER BY total_pipeline_ms)} is a PostgreSQL
     *       ordered-set aggregate. It scans the matching rows sorted by the target column; the
     *       existing {@code IDX_PIPELINE_METRICS_CREATED_AT} index prunes the window, then
     *       the aggregate sorts the result set in memory — acceptable for the row counts expected
     *       in typical reporting windows.</li>
     *   <li>{@code COALESCE(SUM(col), 0)} handles the empty-window case where {@code SUM}
     *       returns NULL. {@code percentile_cont} over an empty set returns NULL directly —
     *       callers must handle null latency values.</li>
     *   <li>Token sums use {@code COALESCE(col, 0)} inside the addition because aborted pipelines
     *       store NULL in token columns (the stage never ran, not "ran with zero tokens").</li>
     * </ul>
     *
     * @param since lower bound for the {@code CREATED_AT} filter (inclusive)
     * @return projection of aggregated metrics; always returns exactly one row
     */
    @Query(value = """
            SELECT
                COUNT(*)                                                                            AS total_requests,
                COUNT(CASE WHEN aborted_at IS NOT NULL THEN 1 END)                                 AS aborted_requests,
                COALESCE(SUM(estimated_cost_usd), 0)                                               AS total_cost_usd,
                percentile_cont(0.50) WITHIN GROUP (ORDER BY total_pipeline_ms)                    AS latency_p50_ms,
                percentile_cont(0.95) WITHIN GROUP (ORDER BY total_pipeline_ms)                    AS latency_p95_ms,
                COALESCE(SUM(COALESCE(contextualization_input_tokens, 0)
                           + COALESCE(generation_input_tokens, 0)), 0)                             AS total_prompt_tokens,
                COALESCE(SUM(COALESCE(contextualization_output_tokens, 0)
                           + COALESCE(generation_output_tokens, 0)), 0)                            AS total_completion_tokens,
                COALESCE(SUM(COALESCE(embedding_tokens, 0)
                           + COALESCE(quality_embedding_tokens, 0)), 0)                            AS total_embedding_tokens
            FROM product.PIPELINE_METRICS
            WHERE created_at >= :since
            """, nativeQuery = true)
    PipelineMetricsSummaryProjection fetchSummary(@Param("since") Instant since);
}
