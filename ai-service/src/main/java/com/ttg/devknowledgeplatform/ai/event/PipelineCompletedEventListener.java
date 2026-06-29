package com.ttg.devknowledgeplatform.ai.event;

import com.ttg.devknowledgeplatform.ai.config.MonitoringConfig;
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
    private final MonitoringConfig monitoring;

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
    // TODO: Move these into MonitoringConfig (or a dedicated PricingConfig) so rate changes
    //       require only a config update rather than a code change + redeploy.
    // TODO: Add a PRICING_VERSION column to PIPELINE_METRICS so historical rows remain
    //       unambiguous after a rate change (e.g. "gpt-4o-mini-2026-06").
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
