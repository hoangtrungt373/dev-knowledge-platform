package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.MetricsPeriod;
import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummary;

/**
 * Provides aggregated cost and latency statistics over a rolling time window
 * of RAG pipeline executions.
 *
 * <p>Backed by a single native PostgreSQL query using ordered-set aggregate
 * functions ({@code percentile_cont}) to compute latency percentiles alongside
 * cost and token-usage sums — all in one round-trip to the database.
 */
public interface PipelineMetricsSummaryService {

    /**
     * Returns the aggregated pipeline metrics for the given reporting window.
     *
     * <p>The window is a rolling lookback: the start boundary is computed as
     * {@code Instant.now().minus(period.getLookback())} at query time. All
     * executions whose {@code CREATED_AT} falls within the window are included,
     * regardless of whether they succeeded or were aborted.
     *
     * @param period the lookback window; must not be {@code null}
     * @return an aggregated summary; never {@code null}
     */
    PipelineMetricsSummary getSummary(MetricsPeriod period);
}
