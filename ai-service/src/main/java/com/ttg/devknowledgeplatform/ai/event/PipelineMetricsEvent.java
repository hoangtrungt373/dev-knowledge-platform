package com.ttg.devknowledgeplatform.ai.event;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;

/**
 * Spring application event published once per RAG pipeline execution.
 *
 * <p>Published by {@link com.ttg.devknowledgeplatform.ai.service.impl.RagQueryServiceImpl}
 * immediately after the pipeline completes (or aborts) and answer quality is assessed.
 * The event carries the full pipeline context and quality verdict so that any listener
 * can act without needing additional service calls.
 *
 * <p>Using a record as the event type is intentional: records are immutable, which
 * prevents a listener from accidentally mutating shared state on a different thread.
 *
 * @param ctx     completed pipeline context; spans and retrieval state already populated
 * @param verdict answer quality verdict; {@code null} for aborted pipelines where
 *                no LLM generation occurred
 */
public record PipelineMetricsEvent(RagPipelineContext ctx, AnswerQualityVerdict verdict) {
}
