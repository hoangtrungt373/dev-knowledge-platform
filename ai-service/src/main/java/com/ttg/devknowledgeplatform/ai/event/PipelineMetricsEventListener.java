package com.ttg.devknowledgeplatform.ai.event;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.StageSpan;
import com.ttg.devknowledgeplatform.ai.entity.PipelineMetrics;
import com.ttg.devknowledgeplatform.ai.repository.PipelineMetricsRepository;
import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists one {@link PipelineMetrics} row per RAG pipeline execution by listening
 * for {@link PipelineMetricsEvent}.
 *
 * <p>Co-located with {@link PipelineMetricsEvent} in the {@code ai.event} package so that
 * the event and its primary consumer are navigable together — the same grouping used by
 * {@code ContentPublishedEvent} and {@code ContentPublishedEventListener} in {@code api}.
 *
 * <p>Async dispatch, MDC trace propagation, timing, and exception safety are all
 * provided by {@link AsyncEventHandler}. This class only contains the mapping logic
 * from event → entity.
 *
 * <p>Nullable columns are populated only when the corresponding pipeline stage ran:
 * <ul>
 *   <li>Chunk counts are {@code null} when the pipeline aborted before {@code RetrievalStage}.</li>
 *   <li>{@code evidenceMeanScore} is {@code null} when the pipeline aborted before
 *       {@code EvidenceQualityStage}.</li>
 *   <li>Answer quality columns are {@code null} for aborted pipelines or when the
 *       quality check was skipped.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PipelineMetricsEventListener extends AsyncEventHandler<PipelineMetricsEvent> {

    private final PipelineMetricsRepository repository;

    /**
     * Exposes the pipeline trace ID so {@link AsyncEventHandler} can bind it to
     * {@code MDC["traceId"]} for the duration of this handler invocation.
     */
    @Override
    protected String resolveTraceId(PipelineMetricsEvent event) {
        return event.ctx().getTraceId().toString();
    }

    /**
     * Maps the event payload to a {@link PipelineMetrics} entity and persists it.
     *
     * @param event published by {@link com.ttg.devknowledgeplatform.ai.service.impl.RagQueryServiceImpl}
     *              after every pipeline execution
     */
    @Override
    protected void doHandle(PipelineMetricsEvent event) {
        RagPipelineContext ctx = event.ctx();
        AnswerQualityVerdict verdict = event.verdict();

        PipelineMetrics metrics = new PipelineMetrics();
        metrics.setTraceId(ctx.getTraceId().toString());
        metrics.setCreatedAt(Instant.now());

        metrics.setAbortedAt(ctx.isAborted()
                ? ctx.getSpans().stream()
                        .filter(StageSpan::aborted)
                        .map(StageSpan::stage)
                        .findFirst()
                        .orElse("unknown")
                : null);

        if (ctx.getCandidates() != null) {
            metrics.setCandidateCount(ctx.getCandidates().size());
        }
        if (ctx.getScoredChunks() != null) {
            metrics.setAfterScoringCount(ctx.getScoredChunks().size());
        }
        if (ctx.getSelectedChunks() != null) {
            metrics.setSelectedCount(ctx.getSelectedChunks().size());
        }

        metrics.setEvidenceMeanScore(ctx.getEvidenceMeanScore());
        metrics.setEffectiveSimThreshold(ctx.getEffectiveSimilarityThreshold());

        if (verdict != null && !verdict.wasSkipped()) {
            metrics.setAnswerContextSim(verdict.contextSimilarity());
            metrics.setAnswerQuerySim(verdict.querySimilarity());
            metrics.setAnswerDrifted(verdict.drifted());
        }

        repository.save(metrics);
        log.debug("Pipeline metrics recorded [traceId={}]", ctx.getTraceId());
    }
}
