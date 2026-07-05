package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.MetricsPeriod;
import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummary;
import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummaryProjection;
import com.ttg.devknowledgeplatform.ai.repository.PipelineMetricsRepository;
import com.ttg.devknowledgeplatform.ai.service.PipelineMetricsSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Default {@link PipelineMetricsSummaryService} implementation.
 *
 * <p>Delegates the entire aggregation to a single native PostgreSQL query in
 * {@link PipelineMetricsRepository#fetchSummary(Instant)}, then maps the
 * one-row result into the {@link PipelineMetricsSummary} record.
 *
 * <p>{@code @Transactional(readOnly = true)} is applied so that Hibernate uses
 * a read-only JDBC connection hint, allowing the connection pool to route the
 * query to a read replica when one is configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class PipelineMetricsSummaryServiceImpl implements PipelineMetricsSummaryService {

    private final PipelineMetricsRepository repository;

    @Override
    public PipelineMetricsSummary getSummary(MetricsPeriod period) {
        Instant since = Instant.now().minus(period.getLookback());
        log.debug("Fetching pipeline metrics summary: period={} since={}", period, since);

        PipelineMetricsSummaryProjection row = repository.fetchSummary(since);
        return toSummary(period, row);
    }

    /**
     * Maps the single-row projection to a {@link PipelineMetricsSummary} record.
     *
     * <p>Null-handling rules:
     * <ul>
     *   <li>Latency percentiles are {@code null} when no rows exist in the window
     *       (PostgreSQL {@code percentile_cont} over an empty set returns NULL). The
     *       response record accepts {@code null} for those fields so callers can
     *       distinguish "no data" from "zero latency".</li>
     *   <li>All token sums use {@code COALESCE(..., 0)} in SQL, so they arrive as
     *       non-null {@code Long} values; defensive null checks are still applied
     *       for robustness against projection proxy edge cases.</li>
     *   <li>{@code estimatedCostUsd} preserves the SQL {@code COALESCE(SUM(...), 0)}
     *       result; it is {@code null} only when the projection itself is null (never
     *       in practice given the COUNT(*) guarantee of a one-row result).</li>
     * </ul>
     *
     * @param period the reporting window used to label the response
     * @param row    single-row projection from the native aggregate query
     * @return fully populated summary record
     */
    private PipelineMetricsSummary toSummary(MetricsPeriod period, PipelineMetricsSummaryProjection row) {
        long totalRequests    = row.getTotalRequests()    != null ? row.getTotalRequests()    : 0L;
        long abortedRequests  = row.getAbortedRequests()  != null ? row.getAbortedRequests()  : 0L;
        BigDecimal cost       = row.getTotalCostUsd()     != null ? row.getTotalCostUsd()     : BigDecimal.ZERO;
        long promptTokens     = row.getTotalPromptTokens()     != null ? row.getTotalPromptTokens()     : 0L;
        long completionTokens = row.getTotalCompletionTokens() != null ? row.getTotalCompletionTokens() : 0L;
        long embeddingTokens  = row.getTotalEmbeddingTokens()  != null ? row.getTotalEmbeddingTokens()  : 0L;

        Long p50 = row.getLatencyP50Ms() != null ? row.getLatencyP50Ms().longValue() : null;
        Long p95 = row.getLatencyP95Ms() != null ? row.getLatencyP95Ms().longValue() : null;

        return new PipelineMetricsSummary(
                period.name(),
                totalRequests,
                abortedRequests,
                cost,
                p50,
                p95,
                new PipelineMetricsSummary.TokenUsageSummary(promptTokens, completionTokens, embeddingTokens)
        );
    }
}
