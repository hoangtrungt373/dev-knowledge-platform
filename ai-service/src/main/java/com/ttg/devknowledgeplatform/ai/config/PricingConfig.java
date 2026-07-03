package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the per-token USD rates used to estimate request cost.
 *
 * <p>Bound from the {@code app.ai.pricing} prefix. Consumed by
 * {@code PipelineCompletedEventListener#computeEstimatedCost(RagPipelineContext)} to populate
 * {@code PipelineMetrics.estimatedCostUsd}, which in turn feeds the {@code HIGH_COST} alert
 * threshold ({@link MonitoringConfig#getHighCostThresholdUsd()}) and the pipeline-metrics
 * summary endpoint's cost aggregate.
 *
 * <p>Chat rates are keyed by model id — the same id used in {@link ChatModelsConfig#getProfiles()}
 * and sent by clients as {@code ChatRequest.chatModel} — since different models, and different
 * providers, price input/output tokens differently. Every id configured in
 * {@link ChatModelsConfig} should have a matching entry here; a request served by a model with
 * no pricing entry logs a warning and its LLM-generation cost is omitted from the estimate
 * rather than throwing (the embedding portion of the cost is still computed).
 *
 * <p>These rates are static configuration, not a live lookup against the provider — they must
 * be updated by hand whenever a model is added to {@link ChatModelsConfig} or a provider changes
 * its published rates, or cost estimates will silently drift from reality.
 */
@ConfigurationProperties(prefix = "app.ai.pricing")
@Validated
@Getter
@Setter
public class PricingConfig {

    /**
     * USD cost per token for the configured embedding model, covering both query embedding
     * and the answer-quality-check embedding. Flat (not per-model) because only one embedding
     * model is ever active at a time — see {@link ModelConfig#getModel()}.
     */
    @DecimalMin("0.0")
    private BigDecimal embeddingCostPerToken = new BigDecimal("0.00000002");

    /** Per-token input/output rates, keyed by chat model id (see class Javadoc). */
    @NotEmpty
    @Valid
    private Map<String, ChatModelPricing> chatModels = new LinkedHashMap<>();

    /** Input/output USD-per-token rates for one chat model. */
    @Getter
    @Setter
    public static class ChatModelPricing {

        /** USD cost per input token, covering both the contextualization and generation calls. */
        @DecimalMin("0.0")
        private BigDecimal inputCostPerToken;

        /** USD cost per output token, covering both the contextualization and generation calls. */
        @DecimalMin("0.0")
        private BigDecimal outputCostPerToken;
    }
}
