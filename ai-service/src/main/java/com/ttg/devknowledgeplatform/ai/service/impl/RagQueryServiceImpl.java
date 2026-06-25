package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.exception.RagQueryException;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineRunner;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
public class RagQueryServiceImpl implements RagQueryService {

    private static final String GENERIC_ERROR_MESSAGE =
            "Failed to process your question. Please try again later.";

    private final RagPipelineRunner pipelineRunner;
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;

    @Override
    public RagAnswer query(String question, ConversationContext context, RagFilter filter) {
        log.info("RAG query: history={} turns, hasSummary={}, filter={}",
                context.recentTurns().size(), context.hasSummary(),
                filter.isEmpty() ? "none" : filter);
        try {
            RagPipelineContext pipelineCtx = new RagPipelineContext(question, context, filter);
            pipelineRunner.run(pipelineCtx);

            if (pipelineCtx.isAborted()) {
                return new RagAnswer(pipelineCtx.getAbortReason(), List.of());
            }

            String answer = chatLanguageModel.generate(pipelineCtx.getMessages()).content().text();
            log.info("RAG query completed: {} sources, answer length={}",
                    pipelineCtx.getSources().size(), answer.length());
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
                            RagFilter filter, RagStreamHandler handler) {
        log.info("RAG stream query: history={} turns, hasSummary={}, filter={}",
                context.recentTurns().size(), context.hasSummary(),
                filter.isEmpty() ? "none" : filter);
        try {
            RagPipelineContext pipelineCtx = new RagPipelineContext(question, context, filter);
            pipelineRunner.run(pipelineCtx);

            if (pipelineCtx.isAborted()) {
                handler.onToken(pipelineCtx.getAbortReason());
                handler.onComplete();
                return;
            }

            // Send source citations before LLM generation so the client can render them immediately
            handler.onSources(pipelineCtx.getSources());

            streamingChatLanguageModel.generate(
                    pipelineCtx.getMessages(),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            handler.onToken(token);
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            log.info("RAG stream completed: {} sources", pipelineCtx.getSources().size());
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
}
