package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.StageSpan;
import com.ttg.devknowledgeplatform.ai.event.PipelineCompletedEvent;
import com.ttg.devknowledgeplatform.ai.exception.RagQueryException;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineRunner;
import com.ttg.devknowledgeplatform.ai.service.AnswerQualityService;
import com.ttg.devknowledgeplatform.ai.service.ChatModelResolver;
import com.ttg.devknowledgeplatform.ai.service.ConversationTopicGuardService;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.ai.dto.ConversationContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link RagQueryService} implementation.
 *
 * <p>This class is intentionally thin: it delegates the entire retrieval pipeline to
 * {@link RagPipelineRunner} and is responsible only for:
 * <ol>
 *   <li>Creating a {@link RagPipelineContext} from the incoming request.</li>
 *   <li>Running the pipeline (contextualise → embed → retrieve → score → dedup → MMR → build messages).</li>
 *   <li>Invoking the LLM — either blocking ({@link ChatLanguageModel}) or streaming
 *       ({@link StreamingChatLanguageModel}) — with the assembled message list.</li>
 *   <li>Returning a {@link RagAnswer} or dispatching SSE events via {@link RagStreamHandler}.</li>
 * </ol>
 *
 * <p>No {@code @Transactional} is placed on this class intentionally: the repository calls
 * inside the pipeline each run in their own short-lived read-only transaction, and the DB
 * connection is fully released before the LLM HTTP call begins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class RagQueryServiceImpl implements RagQueryService {

    private static final String GENERIC_ERROR_MESSAGE =
            "Failed to process your question. Please try again later.";

    private final RagPipelineRunner pipelineRunner;
    private final ChatModelResolver chatModelResolver;
    private final AnswerQualityService answerQualityService;
    private final ConversationTopicGuardService conversationTopicGuardService;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RagAnswer query(String question, ConversationContext context, RagFilter filter, Integer userId, String chatModel) {
        log.info("RAG query: history={} turns, hasSummary={}, filter={}, userId={}, chatModel={}",
                context.recentTurns().size(), context.hasSummary(),
                filter.isEmpty() ? "none" : filter, userId, chatModel);
        // Resolved before any pipeline work starts: an unsupported model id must reject the
        // request outright (400), not after retrieval has already spent embedding calls.
        ChatLanguageModel model = chatModelResolver.resolveBlocking(chatModel);
        try {
            ConversationContext effectiveContext = conversationTopicGuardService.guard(question, context);
            RagPipelineContext pipelineCtx = new RagPipelineContext(question, effectiveContext, filter);
            pipelineCtx.setUserId(userId);
            pipelineCtx.setResolvedChatModel(chatModelResolver.resolveModelId(chatModel));
            pipelineRunner.run(pipelineCtx);
            recordPipelineMetrics(pipelineCtx);

            if (pipelineCtx.isAborted()) {
                log.info("RAG query [traceId={}] pipeline aborted — returning soft answer", pipelineCtx.getTraceId());
                eventPublisher.publishEvent(new PipelineCompletedEvent(pipelineCtx, null));
                return new RagAnswer(pipelineCtx.getAbortReason(), List.of());
            }

            long llmStart = System.currentTimeMillis();
            Response<AiMessage> generationResponse = model.generate(pipelineCtx.getMessages());
            pipelineCtx.setLlmGenerationMs(System.currentTimeMillis() - llmStart);
            captureGenerationTokens(pipelineCtx, generationResponse.tokenUsage());

            String answer = generationResponse.content().text();
            AnswerQualityVerdict verdict = assessAnswerQuality(answer, pipelineCtx);
            eventPublisher.publishEvent(new PipelineCompletedEvent(pipelineCtx, verdict));
            log.info("RAG query [traceId={}] completed: {} sources, answer length={}, llmMs={}",
                    pipelineCtx.getTraceId(), pipelineCtx.getSources().size(),
                    answer.length(), pipelineCtx.getLlmGenerationMs());
            return new RagAnswer(answer, pipelineCtx.getSources());
        } catch (RagQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG query failed: {}", e.getMessage(), e);
            throw new RagQueryException(GENERIC_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void queryStream(String question, ConversationContext context,
                            RagFilter filter, Integer userId, String chatModel, RagStreamHandler handler) {
        log.info("RAG stream query: history={} turns, hasSummary={}, filter={}, userId={}, chatModel={}",
                context.recentTurns().size(), context.hasSummary(),
                filter.isEmpty() ? "none" : filter, userId, chatModel);
        // Resolved before any pipeline work starts: an unsupported model id must reject the
        // request outright (400), not after retrieval has already spent embedding calls.
        StreamingChatLanguageModel model = chatModelResolver.resolveStreaming(chatModel);
        try {
            ConversationContext effectiveContext = conversationTopicGuardService.guard(question, context);
            RagPipelineContext pipelineCtx = new RagPipelineContext(question, effectiveContext, filter);
            pipelineCtx.setUserId(userId);
            pipelineCtx.setResolvedChatModel(chatModelResolver.resolveModelId(chatModel));
            pipelineRunner.run(pipelineCtx);
            recordPipelineMetrics(pipelineCtx);

            if (pipelineCtx.isAborted()) {
                log.info("RAG stream [traceId={}] pipeline aborted — returning soft answer", pipelineCtx.getTraceId());
                eventPublisher.publishEvent(new PipelineCompletedEvent(pipelineCtx, null));
                handler.onToken(pipelineCtx.getAbortReason());
                handler.onComplete();
                return;
            }

            // Send source citations before LLM generation so the client can render them immediately
            handler.onSources(pipelineCtx.getSources());

            final long llmStart = System.currentTimeMillis();
            model.generate(
                    pipelineCtx.getMessages(),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            handler.onToken(token);
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            pipelineCtx.setLlmGenerationMs(System.currentTimeMillis() - llmStart);
                            captureGenerationTokens(pipelineCtx, response.tokenUsage());
                            AnswerQualityVerdict verdict = assessAnswerQuality(response.content().text(), pipelineCtx);
                            eventPublisher.publishEvent(new PipelineCompletedEvent(pipelineCtx, verdict));
                            log.info("RAG stream [traceId={}] completed: {} sources, llmMs={}",
                                    pipelineCtx.getTraceId(), pipelineCtx.getSources().size(),
                                    pipelineCtx.getLlmGenerationMs());
                            handler.onComplete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("RAG stream LLM error: {}", error.getMessage(), error);
                            handler.onError(new RagQueryException("Streaming failed during generation.", error));
                        }
                    }
            );
        } catch (RagQueryException e) {
            handler.onError(e);
        } catch (Exception e) {
            log.error("RAG stream query failed: {}", e.getMessage(), e);
            handler.onError(new RagQueryException(GENERIC_ERROR_MESSAGE, e));
        }
    }

    /**
     * Copies input/output token counts from the LangChain4j {@link TokenUsage} of the final
     * LLM generation call onto the pipeline context. All counts are nullable — defensive null
     * checks prevent any monitoring failure from propagating into the main response path.
     */
    private void captureGenerationTokens(RagPipelineContext ctx, TokenUsage usage) {
        if (usage == null) return;
        if (usage.inputTokenCount() != null)  ctx.setGenerationInputTokens(usage.inputTokenCount());
        if (usage.outputTokenCount() != null) ctx.setGenerationOutputTokens(usage.outputTokenCount());
    }

    /**
     * Records Micrometer metrics for a completed (or aborted) pipeline run.
     *
     * <p>Three instrument families are updated:
     * <ul>
     *   <li><strong>{@code rag.stage.latency}</strong> ({@link Timer}) — one sample per executed stage,
     *       tagged by stage name. Provides p50/p95/p99 per stage across all requests.</li>
     *   <li><strong>{@code rag.pipeline.requests}</strong> ({@link Counter}) — incremented once per
     *       pipeline run, tagged by {@code outcome}: {@code "success"} or
     *       {@code "aborted.<StageName>"} (e.g. {@code "aborted.EvidenceQualityStage"}).</li>
     *   <li><strong>Retrieval funnel</strong> ({@link DistributionSummary}) — three instruments
     *       recording candidate count, post-scoring count, and post-MMR selected count.
     *       Null-guarded: instruments are only recorded when the corresponding stage ran.</li>
     * </ul>
     *
     * <p>Failures are caught and logged; a metrics error must never disrupt the response path.
     * All six instruments are visible at {@code /actuator/metrics}.
     *
     * @param ctx completed pipeline context (spans and retrieval state already populated)
     */
    private void recordPipelineMetrics(RagPipelineContext ctx) {
        try {
            ctx.getSpans().forEach(span ->
                    Timer.builder("rag.stage.latency")
                            .description("Wall-clock execution time per RAG pipeline stage")
                            .tag("stage", span.stage())
                            .register(meterRegistry)
                            .record(span.durationMs(), TimeUnit.MILLISECONDS));

            String outcome = ctx.isAborted()
                    ? "aborted." + ctx.getSpans().stream()
                            .filter(StageSpan::aborted)
                            .map(StageSpan::stage)
                            .findFirst()
                            .orElse("unknown")
                    : "success";
            Counter.builder("rag.pipeline.requests")
                    .description("Total RAG pipeline executions labelled by outcome or aborted stage name")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();

            if (ctx.getCandidates() != null) {
                DistributionSummary.builder("rag.retrieval.candidates")
                        .description("Candidate chunks returned by pgvector ANN search")
                        .register(meterRegistry)
                        .record(ctx.getCandidates().size());
            }
            if (ctx.getScoredChunks() != null) {
                DistributionSummary.builder("rag.retrieval.after_scoring")
                        .description("Chunks surviving ScoringStage and RetrievalAnomalyStage filters")
                        .register(meterRegistry)
                        .record(ctx.getScoredChunks().size());
            }
            if (ctx.getSelectedChunks() != null) {
                DistributionSummary.builder("rag.retrieval.selected")
                        .description("Chunks selected by MMR for LLM context")
                        .register(meterRegistry)
                        .record(ctx.getSelectedChunks().size());
            }
        } catch (Exception e) {
            log.warn("Pipeline metrics recording failed [traceId={}] — skipping (cause: {})",
                    ctx.getTraceId(), e.getMessage());
        }
    }

    /**
     * Runs the post-generation answer quality check (Case 6 — monitoring only) and returns
     * the verdict so callers can publish it via {@link PipelineCompletedEvent}.
     *
     * <p>Failures are caught and logged; a failed assessment returns
     * {@link AnswerQualityVerdict#skipped()} so callers always receive a non-null verdict.
     * A failed assessment must not affect the answer already returned to the user.
     *
     * @return the quality verdict, or {@link AnswerQualityVerdict#skipped()} on error
     */
    private AnswerQualityVerdict assessAnswerQuality(String answer, RagPipelineContext pipelineCtx) {
        try {
            AnswerQualityVerdict verdict = answerQualityService.assess(answer, pipelineCtx);
            if (!verdict.wasSkipped() && verdict.drifted()) {
                log.warn("Answer drift detected [traceId={}] — contextSimilarity={} querySimilarity={}",
                        pipelineCtx.getTraceId(), verdict.contextSimilarity(), verdict.querySimilarity());
            }
            return verdict;
        } catch (Exception e) {
            log.warn("Answer quality check failed [traceId={}] — skipping (cause: {})",
                    pipelineCtx.getTraceId(), e.getMessage());
            return AnswerQualityVerdict.skipped();
        }
    }
}
