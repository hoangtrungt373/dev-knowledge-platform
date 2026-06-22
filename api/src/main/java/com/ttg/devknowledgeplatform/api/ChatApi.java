package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.dto.chat.ChatRequest;
import com.ttg.devknowledgeplatform.dto.chat.ChatResponse;
import com.ttg.devknowledgeplatform.dto.chat.ChatSessionHistoryDto;
import com.ttg.devknowledgeplatform.dto.chat.ChatSessionSummaryDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * HTTP contract for the RAG-powered chat API.
 *
 * <p>This interface is the single source of truth for the chat endpoint's HTTP contract:
 * URL mappings, accepted media types, parameter bindings, and validation constraints all
 * live here. The implementation class ({@link com.ttg.devknowledgeplatform.api.impl.ChatController}) contains only delegation
 * logic and carries no HTTP annotations.
 *
 * <p>OpenAPI documentation annotations ({@code @Tag}, {@code @Operation},
 * {@code @ApiResponse}, {@code @Parameter}) should be added to this interface when
 * springdoc-openapi is introduced — the implementation will remain untouched.
 */
@RequestMapping("/api/v1/chat")
public interface ChatApi {

    /**
     * Answers a question using the RAG pipeline (blocking).
     *
     * @param request validated chat request; {@code sessionId} is optional — omit to start a new session
     * @param userId  authenticated user's integer primary key, injected from the JWT
     * @return {@code 200} with answer, sources and active session ID;
     *         {@code 429} if the rate limit is exceeded;
     *         {@code 404} if {@code sessionId} does not belong to the caller
     */
    @PostMapping
    ResponseEntity<ChatResponse> chat(@RequestBody @Valid ChatRequest request,
                                      @CurrentUserId Integer userId);

    /**
     * Answers a question using the RAG pipeline, streaming tokens via Server-Sent Events.
     *
     * <p>Event sequence: {@code session} → {@code sources} → {@code token} (repeating) → {@code done}.
     *
     * @param request validated chat request; {@code sessionId} is optional
     * @param userId  authenticated user's integer primary key, injected from the JWT
     * @return an open {@link SseEmitter}; the HTTP thread returns immediately
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chatStream(@RequestBody @Valid ChatRequest request,
                          @CurrentUserId Integer userId);

    /**
     * Lists all sessions for the current user, ordered by most recent activity.
     *
     * @param userId authenticated user's integer primary key, injected from the JWT
     * @return {@code 200} with session summaries (ID, title, last activity, message count)
     */
    @GetMapping("/sessions")
    ResponseEntity<List<ChatSessionSummaryDto>> listSessions(@CurrentUserId Integer userId);

    /**
     * Returns the full message history for a session.
     *
     * @param id     session primary key
     * @param userId used to verify ownership — returns {@code 404} if the session belongs to another user
     * @return {@code 200} with all messages ordered by turn index
     */
    @GetMapping("/sessions/{id}")
    ResponseEntity<ChatSessionHistoryDto> getSessionHistory(@PathVariable Integer id,
                                                            @CurrentUserId Integer userId);
}
