package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;

/**
 * Port for persisting per-request pipeline quality metrics after each RAG execution.
 *
 * <p>This interface is the domain side of a Ports-and-Adapters boundary: {@code ai-service}
 * defines what data must be recorded; {@code api} provides the JPA adapter that writes it
 * to the {@code PIPELINE_METRICS} table. The separation keeps {@code ai-service} free of
 * any JPA or database dependency while allowing {@link com.ttg.devknowledgeplatform.ai.service.impl.RagQueryServiceImpl}
 * to trigger persistence without knowing the storage mechanism.
 *
 * <p>Implementations are expected to run asynchronously so that a slow DB write does not
 * add latency to the user response.
 */
public interface PipelineMetricsRecorder {

    /**
     * Records pipeline metrics for a single request execution.
     *
     * <p>Called in two scenarios:
     * <ul>
     *   <li><strong>Aborted pipeline</strong> — called immediately after
     *       {@link com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineRunner#run} returns;
     *       {@code verdict} is {@code null} because no LLM generation occurred.</li>
     *   <li><strong>Successful pipeline</strong> — called after answer quality assessment
     *       completes (both blocking and streaming paths); {@code verdict} carries the two
     *       similarity scores and drift flag.</li>
     * </ul>
     *
     * @param ctx     completed pipeline context; spans and retrieval state already populated
     * @param verdict answer quality verdict, or {@code null} for aborted pipelines
     */
    void record(RagPipelineContext ctx, AnswerQualityVerdict verdict);
}
