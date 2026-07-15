package com.ttg.devknowledgeplatform.ai.api;

import com.ttg.devknowledgeplatform.ai.dto.MetricsPeriod;
import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the pipeline cost and latency monitoring API.
 *
 * <p>All endpoints are mounted under {@code /api/v1/admin/pipeline-metrics} and are
 * therefore covered by the {@code hasRole("ADMIN")} rule in {@code SecurityConfig}.
 * No {@code @PreAuthorize} is needed on individual methods.
 *
 * <p>HTTP contract (URL mappings, media types, parameter bindings) lives here;
 * the implementation ({@link com.ttg.devknowledgeplatform.ai.api.impl.PipelineMetricsController})
 * carries only delegation logic.
 */
@RequestMapping("/api/v1/admin/pipeline-metrics")
public interface PipelineMetricsApi {

    /**
     * Returns aggregated cost and latency statistics for a rolling reporting window.
     *
     * <p>The window is a lookback from now: {@code LAST_24H} covers the last 24 hours,
     * {@code LAST_7_DAYS} the last 7 days, and {@code LAST_30_DAYS} the last 30 days.
     * The result includes all pipeline executions — both successful and aborted — that
     * started within the window.
     *
     * <p>Latency percentiles ({@code latencyP50Ms}, {@code latencyP95Ms}) are {@code null}
     * when no requests were recorded in the window.
     *
     * @param period lookback window; defaults to {@code LAST_7_DAYS} when omitted
     * @return {@code 200} with the aggregated summary
     */
    @GetMapping("/summary")
    ResponseEntity<PipelineMetricsSummary> getSummary(
            @RequestParam(defaultValue = "LAST_7_DAYS") MetricsPeriod period);
}
