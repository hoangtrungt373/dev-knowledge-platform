package com.ttg.devknowledgeplatform.ai.api.impl;

import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.ai.api.ChatApi;
import com.ttg.devknowledgeplatform.ai.dto.ConversationContext;
import com.ttg.devknowledgeplatform.ai.entity.ChatMessage;
import com.ttg.devknowledgeplatform.ai.config.sse.SseStreamTemplate;
import com.ttg.devknowledgeplatform.ai.dto.chat.ChatRequest;
import com.ttg.devknowledgeplatform.ai.dto.chat.ChatResponse;
import com.ttg.devknowledgeplatform.ai.dto.chat.ChatSessionHistoryDto;
import com.ttg.devknowledgeplatform.ai.dto.chat.ChatSessionSummaryDto;
import com.ttg.devknowledgeplatform.ai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ChatApi}.
 *
 * <p>Contains no HTTP annotations — those live entirely on {@link ChatApi}.
 * This class is responsible only for orchestrating the RAG pipeline, session
 * management, and SSE streaming for each use case.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController implements ChatApi {

    /** Number of prior Q&A pairs (2 messages each) prepended to the LLM context. */
    private static final int MAX_CONTEXT_TURNS = 5;

    private final RagQueryService ragQueryService;
    private final ChatSessionService chatSessionService;
    private final SseStreamTemplate sseStreamTemplate;

    @Override
    public ResponseEntity<ChatResponse> chat(ChatRequest request, Integer userId) {
        Integer sessionId = chatSessionService.getOrCreateSessionId(request.sessionId(), userId);
        ConversationContext context = chatSessionService.getConversationContext(sessionId, MAX_CONTEXT_TURNS);

        var answer = ragQueryService.query(request.question(), context, buildFilter(request), userId, request.chatModel());
        chatSessionService.addTurn(sessionId, request.question(), answer.answer());

        log.info("Chat query completed: sessionId={} questionLength={}", sessionId, request.question().length());
        return ResponseEntity.ok(ChatResponse.from(answer, sessionId));
    }

    @Override
    public SseEmitter chatStream(ChatRequest request, Integer userId) {
        Integer sessionId = chatSessionService.getOrCreateSessionId(request.sessionId(), userId);
        ConversationContext context = chatSessionService.getConversationContext(sessionId, MAX_CONTEXT_TURNS);
        StringBuilder answerBuffer = new StringBuilder();

        return sseStreamTemplate.stream(writer -> {
            writer.send("session", Map.of("sessionId", sessionId));
            ragQueryService.queryStream(request.question(), context, buildFilter(request), userId, request.chatModel(), new RagStreamHandler() {
                @Override
                public void onSources(List<RagSource> sources) {
                    writer.send("sources", sources);
                }

                @Override
                public void onToken(String token) {
                    answerBuffer.append(token);
                    writer.send("token", token);
                }

                @Override
                public void onComplete() {
                    chatSessionService.addTurn(sessionId, request.question(), answerBuffer.toString());
                    log.info("Chat stream completed: sessionId={}", sessionId);
                    writer.send("done", "[DONE]");
                    writer.complete();
                }

                @Override
                public void onError(Throwable error) {
                    writer.completeWithError(error);
                }
            });
        });
    }

    /** Constructs a {@link RagFilter} from the optional filter fields on the chat request. */
    private RagFilter buildFilter(ChatRequest request) {
        return new RagFilter(request.sourceTypes(), request.tags(), request.categoryId());
    }

    @Override
    public ResponseEntity<List<ChatSessionSummaryDto>> listSessions(Integer userId) {
        return ResponseEntity.ok(chatSessionService.listSessions(userId));
    }

    @Override
    public ResponseEntity<ChatSessionHistoryDto> getSessionHistory(Integer id, Integer userId) {
        List<ChatMessage> messages = chatSessionService.getHistory(id, userId);
        List<ChatSessionHistoryDto.MessageDto> dtos = messages.stream()
                .map(m -> new ChatSessionHistoryDto.MessageDto(
                        m.getRole().name(), m.getContent(), m.getTurnIndex()))
                .toList();
        return ResponseEntity.ok(new ChatSessionHistoryDto(id, dtos));
    }
}
