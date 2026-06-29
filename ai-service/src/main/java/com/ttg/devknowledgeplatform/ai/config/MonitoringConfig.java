package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Configuration properties for per-request cost and latency alerting.
 *
 * <p>Bound from the {@code app.ai.monitoring} prefix. Auto-registered by
 * {@code @ConfigurationPropertiesScan} on the main application class — no explicit
 * {@code @EnableConfigurationProperties} is needed.
 *
 * <h3>Disable semantics</h3>
 * <p>Setting a threshold to {@code 0} disables the corresponding check:
 * <ul>
 *   <li>{@code slow-request-threshold-ms: 0} — no {@code SLOW_REQUEST} log events</li>
 *   <li>{@code high-cost-threshold-usd: 0} — no {@code HIGH_COST} log events</li>
 * </ul>
 * This avoids the need for a separate {@code enabled} flag and mirrors the convention used
 * by {@link GuardConfig#conversationTopicShiftThreshold} (set to {@code 0.0} to disable).
 *
 * <h3>Alert detection boundary</h3>
 * <p>Checks fire inside {@code PipelineCompletedEventListener} immediately after the
 * {@code PIPELINE_METRICS} row is persisted — detection happens on every request and
 * produces a structured {@code WARN} log that an external aggregator (Grafana Loki, ELK,
 * Datadog) can turn into a notification without any application code change.
 */
@ConfigurationProperties(prefix = "app.ai.monitoring")
@Validated
@Getter
@Setter
public class MonitoringConfig {

    /**
     * Wall-clock pipeline latency (in milliseconds) above which a {@code SLOW_REQUEST}
     * warning is emitted. Covers the full end-to-end time from context creation through
     * LLM completion and quality check. Set to {@code 0} to disable.
     */
    @PositiveOrZero
    private long slowRequestThresholdMs = 5000;

    /**
     * Estimated USD cost above which a {@code HIGH_COST} warning is emitted.
     * Computed from raw token counts at record-write time; see
     * {@code PipelineCompletedEventListener.computeEstimatedCost()}.
     * Set to {@code 0} to disable.
     */
    @DecimalMin("0.0")
    private BigDecimal highCostThresholdUsd = new BigDecimal("0.01");
}
