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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Persists one {@link PipelineMetrics} row per RAG pipeline execution by listening
 * for {@link PipelineCompletedEvent}.
 *
 * <p>Co-located with {@link PipelineCompletedEvent} in the {@code ai.event} package so that
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
public class PipelineCompletedEventListener extends AsyncEventHandler<PipelineCompletedEvent> {

    private final PipelineMetricsRepository repository;

    /**
     * Exposes the pipeline trace ID so {@link AsyncEventHandler} can bind it to
     * {@code MDC["traceId"]} for the duration of this handler invocation.
     */
    @Override
    protected String resolveTraceId(PipelineCompletedEvent event) {
        return event.ctx().getTraceId().toString();
    }

    // Pricing constants for cost estimation (as of 2026-06; update if OpenAI changes rates).
    // gpt-4o-mini: $0.15/1M input tokens, $0.60/1M output tokens.
    // text-embedding-3-small: $0.020/1M tokens.
    private static final BigDecimal LLM_INPUT_COST_PER_TOKEN  = new BigDecimal("0.00000015");
    private static final BigDecimal LLM_OUTPUT_COST_PER_TOKEN = new BigDecimal("0.00000060");
    private static final BigDecimal EMBEDDING_COST_PER_TOKEN  = new BigDecimal("0.00000002");

    /**
     * Maps the event payload to a {@link PipelineMetrics} entity and persists it.
     *
     * @param event published by {@link com.ttg.devknowledgeplatform.ai.service.impl.RagQueryServiceImpl}
     *              after every pipeline execution
     */
    @Override
    protected void doHandle(PipelineCompletedEvent event) {
        RagPipelineContext ctx = event.ctx();
        AnswerQualityVerdict verdict = event.verdict();

        PipelineMetrics metrics = new PipelineMetrics();
        metrics.setTraceId(ctx.getTraceId().toString());
        metrics.setCreatedAt(Instant.now());

        // Abort state
        metrics.setAbortedAt(ctx.isAborted()
                ? ctx.getSpans().stream()
                        .filter(StageSpan::aborted)
                        .map(StageSpan::stage)
                        .findFirst()
                        .orElse("unknown")
                : null);

        // Quality metrics
        if (ctx.getCandidates() != null)    metrics.setCandidateCount(ctx.getCandidates().size());
        if (ctx.getScoredChunks() != null)  metrics.setAfterScoringCount(ctx.getScoredChunks().size());
        if (ctx.getSelectedChunks() != null) metrics.setSelectedCount(ctx.getSelectedChunks().size());

        metrics.setEvidenceMeanScore(ctx.getEvidenceMeanScore());
        metrics.setEffectiveSimThreshold(ctx.getEffectiveSimilarityThreshold());

        if (verdict != null && !verdict.wasSkipped()) {
            metrics.setAnswerContextSim(verdict.contextSimilarity());
            metrics.setAnswerQuerySim(verdict.querySimilarity());
            metrics.setAnswerDrifted(verdict.drifted());
        }

        // Feature 1: Stage latencies — derived from StageSpan list; LLM time tracked separately
        metrics.setContextualizationMs(spanMs(ctx, "ContextualizationStage"));
        metrics.setEmbeddingMs(spanMs(ctx, "EmbeddingStage"));
        metrics.setRetrievalMs(spanMs(ctx, "RetrievalStage"));
        metrics.setLlmGenerationMs(ctx.getLlmGenerationMs() > 0 ? ctx.getLlmGenerationMs() : null);
        metrics.setTotalPipelineMs(ctx.elapsedMs());

        // Feature 2: Token usage
        metrics.setContextualizationInputTokens(nullIfZero(ctx.getContextualizationInputTokens()));
        metrics.setContextualizationOutputTokens(nullIfZero(ctx.getContextualizationOutputTokens()));
        metrics.setEmbeddingTokens(nullIfZero(ctx.getEmbeddingTokens()));
        metrics.setQualityEmbeddingTokens(nullIfZero(ctx.getQualityEmbeddingTokens()));
        metrics.setGenerationInputTokens(nullIfZero(ctx.getGenerationInputTokens()));
        metrics.setGenerationOutputTokens(nullIfZero(ctx.getGenerationOutputTokens()));
        metrics.setEstimatedCostUsd(computeEstimatedCost(ctx));

        // Feature 3: User attribution
        metrics.setUserId(ctx.getUserId());

        repository.save(metrics);
        log.debug("Pipeline metrics recorded [traceId={} userId={} totalMs={} estimatedCost={}]",
                ctx.getTraceId(), ctx.getUserId(), ctx.elapsedMs(), metrics.getEstimatedCostUsd());
    }

    /**
     * Looks up the wall-clock duration for a named stage in the span list.
     *
     * @param ctx       pipeline context whose span list is searched
     * @param stageName simple class name of the stage (e.g. {@code "RetrievalStage"})
     * @return duration in ms, or {@code null} if the stage did not execute
     */
    private Long spanMs(RagPipelineContext ctx, String stageName) {
        return ctx.getSpans().stream()
                .filter(s -> stageName.equals(s.stage()))
                .mapToLong(StageSpan::durationMs)
                .boxed()
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@code null} when the value is 0, so that "stage did not run" and
     * "stage ran but somehow consumed zero tokens" are both stored as NULL rather than 0.
     */
    private Integer nullIfZero(int value) {
        return value == 0 ? null : value;
    }

    /**
     * Estimates the USD cost for this request from raw token counts and current model pricing.
     *
     * <p>Pricing model:
     * <ul>
     *   <li>LLM input (contextualization + generation): $0.15 / 1M tokens</li>
     *   <li>LLM output (contextualization + generation): $0.60 / 1M tokens</li>
     *   <li>Embedding (query + quality check): $0.020 / 1M tokens</li>
     * </ul>
     *
     * <p>Returns {@code null} when all token counts are zero — this means the pipeline aborted
     * before any LLM call, so storing $0.00 would be misleading (it implies an LLM ran for free).
     *
     * @param ctx pipeline context carrying raw token counts
     * @return estimated cost as a {@link BigDecimal} with 8 decimal places, or {@code null}
     */
    private BigDecimal computeEstimatedCost(RagPipelineContext ctx) {
        long totalInput = (long) ctx.getContextualizationInputTokens() + ctx.getGenerationInputTokens();
        long totalOutput = (long) ctx.getContextualizationOutputTokens() + ctx.getGenerationOutputTokens();
        long totalEmbedding = (long) ctx.getEmbeddingTokens() + ctx.getQualityEmbeddingTokens();

        if (totalInput == 0 && totalOutput == 0 && totalEmbedding == 0) {
            return null;
        }

        BigDecimal cost = BigDecimal.valueOf(totalInput).multiply(LLM_INPUT_COST_PER_TOKEN)
                .add(BigDecimal.valueOf(totalOutput).multiply(LLM_OUTPUT_COST_PER_TOKEN))
                .add(BigDecimal.valueOf(totalEmbedding).multiply(EMBEDDING_COST_PER_TOKEN));

        return cost.setScale(8, RoundingMode.HALF_UP);
    }
}
