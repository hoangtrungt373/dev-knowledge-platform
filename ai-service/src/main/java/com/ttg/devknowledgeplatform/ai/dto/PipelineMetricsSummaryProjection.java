package com.ttg.devknowledgeplatform.ai.dto;

import java.math.BigDecimal;

/**
 * Spring Data JPA interface projection for the pipeline metrics aggregate query.
 *
 * <p>Column aliases in the native SQL are mapped to these getters by Spring Data using
 * underscore-to-camelCase conversion (e.g. {@code total_requests} → {@code getTotalRequests()}).
 * All values are nullable: {@code getLatencyP50Ms()} and {@code getLatencyP95Ms()} return
 * {@code null} when no rows exist in the reporting window (PostgreSQL {@code percentile_cont}
 * returns NULL over an empty set).
 *
 * <p>This projection is intentionally not a record — Spring Data JPA generates the
 * implementation at runtime via a JDK dynamic proxy, which requires an interface.
 */
public interface PipelineMetricsSummaryProjection {

    /** Total pipeline executions (success + aborted) in the window. */
    Long getTotalRequests();

    /** Executions aborted by a guard stage. */
    Long getAbortedRequests();

    /** Sum of {@code ESTIMATED_COST_USD}; 0 when all rows have NULL cost (early aborts). */
    BigDecimal getTotalCostUsd();

    /**
     * 50th-percentile of {@code TOTAL_PIPELINE_MS}.
     * {@code null} when no rows in the window (PostgreSQL {@code percentile_cont} over empty set).
     */
    Double getLatencyP50Ms();

    /**
     * 95th-percentile of {@code TOTAL_PIPELINE_MS}.
     * {@code null} when no rows in the window.
     */
    Double getLatencyP95Ms();

    /** Sum of all input tokens sent to the LLM across contextualization and generation calls. */
    Long getTotalPromptTokens();

    /** Sum of all output tokens received from the LLM. */
    Long getTotalCompletionTokens();

    /** Sum of all tokens used for embeddings (query + quality check). */
    Long getTotalEmbeddingTokens();
}
