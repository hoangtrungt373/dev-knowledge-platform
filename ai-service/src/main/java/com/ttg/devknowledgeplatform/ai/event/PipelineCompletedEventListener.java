package com.ttg.devknowledgeplatform.ai.event;

import com.ttg.devknowledgeplatform.ai.config.MonitoringConfig;
import com.ttg.devknowledgeplatform.ai.config.PricingConfig;
import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.StageSpan;
import com.ttg.devknowledgeplatform.ai.entity.PipelineMetrics;
import com.ttg.devknowledgeplatform.ai.repository.PipelineMetricsRepository;
import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import com.ttg.devknowledgeplatform.infra.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static java.math.BigDecimal.ZERO;

/**
 * Persists one {@link PipelineMetrics} row per RAG pipeline execution by listening
 * for {@link PipelineCompletedEvent}.
 *
 * <p>Co-located with {@link PipelineCompletedEvent} in the {@code ai.event} package so that
 * the event and its primary consumer are navigable together — the same grouping used by
 * {@code ContentPublishedEvent} and {@code ContentPublishedEventListener} in {@code api}.
 *
 * <p>Async dispatch, MDC trace propagation, timing, and exception safety are all
 * provided by {@link AsyncEventHandler}; this class only contains the one-line {@code @EventHandler}
 * listener shim ({@link #onEvent}) it requires plus the mapping logic from event → entity.
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
    private final MonitoringConfig monitoring;
    private final PricingConfig pricing;

    /**
     * Spring listener entry point — see {@link AsyncEventHandler} class Javadoc for why this
     * concretely-typed method must live here rather than on the generic base class. Does nothing
     * but delegate; all actual logic is in {@link #doHandle}.
     */
    @EventHandler
    public void onEvent(PipelineCompletedEvent event) {
        handle(event);
    }

    /**
     * Exposes the pipeline trace ID so {@link AsyncEventHandler} can bind it to
     * {@code MDC["traceId"]} for the duration of this handler invocation.
     */
    @Override
    protected String resolveTraceId(PipelineCompletedEvent event) {
        return event.ctx().getTraceId().toString();
    }

    // TODO: Add a PRICING_VERSION column to PIPELINE_METRICS so historical rows remain
    //       unambiguous after a rate change (e.g. "gpt-5.4-mini-2026-07"). Rates themselves now
    //       live in PricingConfig (app.ai.pricing.*) — see its Javadoc for what to update when
    //       the chat or embedding model changes.

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

        metrics.setEvidenceMeanScore(toScaledDecimal(ctx.getEvidenceMeanScore()));
        metrics.setEffectiveSimThreshold(toScaledDecimal(ctx.getEffectiveSimilarityThreshold()));

        if (verdict != null && !verdict.wasSkipped()) {
            metrics.setAnswerContextSim(toScaledDecimal(verdict.contextSimilarity()));
            metrics.setAnswerQuerySim(toScaledDecimal(verdict.querySimilarity()));
            metrics.setAnswerDrifted(verdict.drifted());
        }

        // Feature 1: Stage latencies — derived from StageSpan list; LLM time tracked separately
        // TODO: Use StageName.CONTEXTUALIZATION.key() (or ContextualizationStage.class.getSimpleName())
        //       instead of plain strings so stage renames are caught at compile time.
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

        // Chat model attribution — id of the profile actually resolved for this request.
        metrics.setChatModel(ctx.getResolvedChatModel());

        // TODO: Extract entity mapping (lines above) to a MapStruct mapper (PipelineMetricsMapper).
        //       doHandle() currently does mapping, cost computation, persistence, and alerting —
        //       each is a distinct responsibility that will grow independently.
        repository.save(metrics);
        log.debug("Pipeline metrics recorded [traceId={} userId={} totalMs={} estimatedCost={}]",
                ctx.getTraceId(), ctx.getUserId(), ctx.elapsedMs(), metrics.getEstimatedCostUsd());

        checkThresholds(metrics, ctx);
    }

    /**
     * Emits structured {@code WARN} log events when the just-persisted row exceeds a
     * configured threshold. Two independent checks fire separately so that alerting rules
     * in a log aggregator (Grafana Loki, ELK, Datadog) can be routed to different teams.
     *
     * <p>Fixed message keys ({@code SLOW_REQUEST}, {@code HIGH_COST}) are intentional —
     * a log aggregator can match on the literal string without a regex, and the key=value
     * pairs carry all diagnostic context needed to investigate without a DB query.
     *
     * <p>Each check is guarded by its threshold being {@code > 0}: setting a threshold to
     * {@code 0} in config disables the corresponding alert without any code change.
     *
     * @param metrics the just-persisted row (all fields already set)
     * @param ctx     the pipeline context for supplementary fields not on the entity
     */
    // TODO: Skip threshold checks when ctx.isAborted() is true — latency for a domain-rejected
    //       query is not actionable and will generate noise in the SLOW_REQUEST alert channel.
    private void checkThresholds(PipelineMetrics metrics, RagPipelineContext ctx) {
        if (monitoring.getSlowRequestThresholdMs() > 0
                && metrics.getTotalPipelineMs() != null
                && metrics.getTotalPipelineMs() > monitoring.getSlowRequestThresholdMs()) {
            log.warn("SLOW_REQUEST traceId={} totalPipelineMs={} thresholdMs={} llmGenerationMs={}"
                            + " contextualizationMs={} userId={} estimatedCostUsd={}",
                    ctx.getTraceId(),
                    metrics.getTotalPipelineMs(),
                    monitoring.getSlowRequestThresholdMs(),
                    metrics.getLlmGenerationMs(),
                    metrics.getContextualizationMs(),
                    ctx.getUserId(),
                    metrics.getEstimatedCostUsd());
        }

        if (monitoring.getHighCostThresholdUsd().compareTo(ZERO) > 0
                && metrics.getEstimatedCostUsd() != null
                && metrics.getEstimatedCostUsd().compareTo(monitoring.getHighCostThresholdUsd()) > 0) {
            log.warn("HIGH_COST traceId={} estimatedCostUsd={} thresholdUsd={}"
                            + " generationInputTokens={} generationOutputTokens={}"
                            + " contextualizationInputTokens={} contextualizationOutputTokens={} userId={}",
                    ctx.getTraceId(),
                    metrics.getEstimatedCostUsd(),
                    monitoring.getHighCostThresholdUsd(),
                    metrics.getGenerationInputTokens(),
                    metrics.getGenerationOutputTokens(),
                    metrics.getContextualizationInputTokens(),
                    metrics.getContextualizationOutputTokens(),
                    ctx.getUserId());
        }
    }

    /**
     * Looks up the wall-clock duration for a named stage in the span list.
     *
     * @param ctx       pipeline context whose span list is searched
     * @param stageName simple class name of the stage (e.g. {@code "RetrievalStage"})
     * @return duration in ms, or {@code null} if the stage did not execute
     */
    // TODO: Replace List<StageSpan> scan with Map<String, Long> on RagPipelineContext so
    //       lookups are O(1). Currently O(n) per lookup × n lookups = O(n²) — acceptable
    //       for ~11 stages today, but noisy if the stage count grows significantly.
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
     * Converts a cosine-similarity {@code float} to the {@code DECIMAL(5, 4)} shape stored in
     * {@code PipelineMetrics}. Values come from {@code float} arithmetic (dot products, means),
     * so they carry binary-to-decimal noise beyond four places; rounding here keeps the stored
     * value consistent with the column's declared scale.
     *
     * @param value raw similarity/threshold value, or {@code null} if the producing stage did not run
     * @return the value rounded to 4 decimal places, or {@code null} if {@code value} is {@code null}
     */
    private BigDecimal toScaledDecimal(Float value) {
        return value == null ? null : toScaledDecimal(value.floatValue());
    }

    private BigDecimal toScaledDecimal(float value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Estimates the USD cost for this request from raw token counts and the per-token rates
     * configured in {@link PricingConfig}, looking up the chat rate by
     * {@link RagPipelineContext#getResolvedChatModel()} since different chat models price
     * differently.
     *
     * <p>Returns {@code null} when all token counts are zero — this means the pipeline aborted
     * before any LLM call, so storing $0.00 would be misleading (it implies an LLM ran for free).
     *
     * <p>If the resolved chat model has no matching entry under {@code app.ai.pricing.chat-models}
     * (e.g. a profile was added to {@code ChatModelsConfig} without a matching pricing entry),
     * the LLM-generation portion of the cost is omitted and a warning is logged — the embedding
     * portion is still computed, so the estimate degrades rather than throwing.
     *
     * @param ctx pipeline context carrying raw token counts and the resolved chat model id
     * @return estimated cost as a {@link BigDecimal} with 8 decimal places, or {@code null}
     */
    private BigDecimal computeEstimatedCost(RagPipelineContext ctx) {
        long totalInput = (long) ctx.getContextualizationInputTokens() + ctx.getGenerationInputTokens();
        long totalOutput = (long) ctx.getContextualizationOutputTokens() + ctx.getGenerationOutputTokens();
        long totalEmbedding = (long) ctx.getEmbeddingTokens() + ctx.getQualityEmbeddingTokens();

        if (totalInput == 0 && totalOutput == 0 && totalEmbedding == 0) {
            return null;
        }

        BigDecimal cost = BigDecimal.valueOf(totalEmbedding).multiply(pricing.getEmbeddingCostPerToken());

        PricingConfig.ChatModelPricing chatPricing = pricing.getChatModels().get(ctx.getResolvedChatModel());
        if (chatPricing != null) {
            cost = cost.add(BigDecimal.valueOf(totalInput).multiply(chatPricing.getInputCostPerToken()))
                    .add(BigDecimal.valueOf(totalOutput).multiply(chatPricing.getOutputCostPerToken()));
        } else if (totalInput > 0 || totalOutput > 0) {
            log.warn("No pricing entry for chat model '{}' [traceId={}] — LLM cost omitted from estimate",
                    ctx.getResolvedChatModel(), ctx.getTraceId());
        }

        return cost.setScale(8, RoundingMode.HALF_UP);
    }
}
