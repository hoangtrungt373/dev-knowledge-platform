package com.ttg.devknowledgeplatform.ai.dto;

/**
 * Immutable snapshot of a single RAG pipeline stage execution, collected into
 * {@link RagPipelineContext#getSpans()} as each stage completes.
 *
 * <p>Together, the span list from a single request forms a lightweight trace of the full
 * pipeline lifecycle — which stages ran, how long each took, and which (if any) triggered
 * an abort. The parent {@link RagPipelineContext#getTraceId()} correlates all spans for a
 * request with every individual stage log line that includes the same trace ID.
 *
 * @param stage      simple class name of the stage (e.g. {@code "RetrievalStage"})
 * @param durationMs wall-clock execution time in milliseconds
 * @param aborted    {@code true} if this stage called {@link RagPipelineContext#abort(String)}
 */
public record StageSpan(String stage, long durationMs, boolean aborted) {}
