package com.ttg.devknowledgeplatform.ai.dto;

import java.math.BigDecimal;

/**
 * Aggregated cost and latency summary for a rolling time window of RAG pipeline executions.
 *
 * <p>Returned by the {@code GET /api/v1/admin/pipeline-metrics/summary} endpoint and produced
 * by a single native PostgreSQL query. All counters are non-negative; {@code estimatedCostUsd}
 * and latency percentiles are {@code null} when no requests were recorded in the period.
 *
 * <p>Java 21 nested records — immutable value objects with auto-generated accessors.
 * Jackson serialises them directly without a separate {@code @JsonProperty} mapping.
 *
 * @param period           name of the reporting window (e.g. {@code "LAST_7_DAYS"})
 * @param totalRequests    number of RAG pipeline executions in the window (success + aborted)
 * @param abortedRequests  number of executions that were aborted by a guard stage
 * @param estimatedCostUsd total USD cost estimated from token counts; {@code null} when no LLM calls ran
 * @param latencyP50Ms     50th-percentile end-to-end latency in ms; {@code null} when no data
 * @param latencyP95Ms     95th-percentile end-to-end latency in ms; {@code null} when no data
 * @param tokenUsage       breakdown of consumed tokens by category
 */
public record PipelineMetricsSummary(
        String period,
        long totalRequests,
        long abortedRequests,
        BigDecimal estimatedCostUsd,
        Long latencyP50Ms,
        Long latencyP95Ms,
        TokenUsageSummary tokenUsage
) {

    /**
     * Token consumption breakdown for a reporting period.
     *
     * @param prompt     total input tokens sent to the LLM (contextualization + generation)
     * @param completion total output tokens received from the LLM (contextualization + generation)
     * @param embedding  total tokens used for embeddings (query embedding + quality check)
     */
    public record TokenUsageSummary(long prompt, long completion, long embedding) {}
}
