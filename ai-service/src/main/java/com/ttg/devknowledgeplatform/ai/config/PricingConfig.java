package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Configuration properties for the per-token USD rates used to estimate request cost.
 *
 * <p>Bound from the {@code app.ai.pricing} prefix. Consumed by
 * {@code PipelineCompletedEventListener#computeEstimatedCost(RagPipelineContext)} to populate
 * {@code PipelineMetrics.estimatedCostUsd}, which in turn feeds the {@code HIGH_COST} alert
 * threshold ({@link MonitoringConfig#getHighCostThresholdUsd()}) and the pipeline-metrics
 * summary endpoint's cost aggregate.
 *
 * <p>These rates are static configuration, not a live lookup against the provider — they must
 * be updated by hand whenever {@link ModelConfig#getChatModel()} or {@link ModelConfig#getModel()}
 * (the embedding model) changes to a different-priced model, or cost estimates will silently
 * drift from reality.
 */
@ConfigurationProperties(prefix = "app.ai.pricing")
@Validated
@Getter
@Setter
public class PricingConfig {

    /**
     * USD cost per input token for the configured chat model, covering both the
     * contextualization call and the final answer-generation call.
     */
    @DecimalMin("0.0")
    private BigDecimal llmInputCostPerToken = new BigDecimal("0.00000075");

    /**
     * USD cost per output token for the configured chat model, covering both the
     * contextualization call and the final answer-generation call.
     */
    @DecimalMin("0.0")
    private BigDecimal llmOutputCostPerToken = new BigDecimal("0.0000045");

    /**
     * USD cost per token for the configured embedding model, covering both query
     * embedding and the answer-quality-check embedding.
     */
    @DecimalMin("0.0")
    private BigDecimal embeddingCostPerToken = new BigDecimal("0.00000002");
}
