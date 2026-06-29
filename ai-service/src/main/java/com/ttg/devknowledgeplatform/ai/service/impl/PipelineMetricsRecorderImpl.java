package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.StageSpan;
import com.ttg.devknowledgeplatform.ai.entity.PipelineMetrics;
import com.ttg.devknowledgeplatform.ai.repository.PipelineMetricsRepository;
import com.ttg.devknowledgeplatform.ai.service.PipelineMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Default {@link PipelineMetricsRecorder} implementation.
 *
 * <p>Runs asynchronously on the {@code sseStreamExecutor} thread pool (configured by
 * {@link com.ttg.devknowledgeplatform.config.web.WebMvcConfig}) so that a slow DB write
 * never adds latency to the user-facing RAG response.
 *
 * <p>Each invocation writes a single {@link PipelineMetrics} row. Nullable columns are
 * populated only when the corresponding pipeline stage ran:
 * <ul>
 *   <li>If the pipeline aborted before {@code RetrievalStage}, chunk counts are {@code null}.</li>
 *   <li>If the pipeline aborted before {@code EvidenceQualityStage}, {@code evidenceMeanScore} is {@code null}.</li>
 *   <li>If {@code verdict} is {@code null} or skipped, all answer quality columns are {@code null}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PipelineMetricsRecorderImpl implements PipelineMetricsRecorder {

    private final PipelineMetricsRepository repository;

    /**
     * Persists a {@link PipelineMetrics} row for the given pipeline execution.
     *
     * @param ctx     completed pipeline context; spans and retrieval state already populated
     * @param verdict answer quality verdict, or {@code null} for aborted pipelines
     */
    @Override
    @Async
    public void record(RagPipelineContext ctx, AnswerQualityVerdict verdict) {
        try {
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
        } catch (Exception e) {
            log.warn("Failed to record pipeline metrics [traceId={}]: {}", ctx.getTraceId(), e.getMessage());
        }
    }
}
