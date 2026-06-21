package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.config.ChatRateLimiter;
import com.ttg.devknowledgeplatform.dto.chat.ChatRequest;
import com.ttg.devknowledgeplatform.dto.chat.ChatResponse;
import com.ttg.devknowledgeplatform.config.sse.SseStreamTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * REST endpoint exposing the RAG-powered chatbot to authenticated users.
 *
 * <p>Delegates to {@link RagQueryService} which embeds the question, retrieves the
 * most relevant knowledge-base chunks via pgvector cosine similarity, and generates
 * a grounded answer using an OpenAI chat model.
 *
 * <p>Access is restricted to authenticated users; no admin role is required so that
 * all registered users can query the knowledge base.
 */
@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class ChatEndpoint {

    private final RagQueryService ragQueryService;
    private final ChatRateLimiter rateLimiter;
    private final SseStreamTemplate sseStreamTemplate;

    /**
     * Accepts a natural-language question and returns an LLM-generated answer
     * together with the source chunks used as context.
     *
     * @param request        validated chat request containing the user's question
     * @param authentication injected by Spring Security — used to key the per-user rate limit bucket
     * @return {@code 200 OK} with a {@link ChatResponse} on success,
     *         {@code 429 Too Many Requests} if the rate limit is exceeded
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody @Valid ChatRequest request,
                                             Authentication authentication) {
        rateLimiter.consume(authentication.getName());
        log.info("Chat request: question length={}", request.question().length());
        RagAnswer answer = ragQueryService.query(request.question());
        return ResponseEntity.ok(ChatResponse.from(answer));
    }

    /**
     * Streams the RAG-generated answer token-by-token using Server-Sent Events.
     *
     * <p>Event sequence:
     * <ol>
     *   <li>{@code event: sources} — JSON array of {@link RagSource} chunks used as context,
     *       sent before LLM generation begins so the client can display citations immediately.</li>
     *   <li>{@code event: token} — one event per generated token.</li>
     *   <li>{@code event: done} — signals the stream is complete.</li>
     * </ol>
     *
     * <p>The embed and retrieval steps run in a virtual thread so the HTTP thread returns
     * the {@link SseEmitter} immediately, establishing the SSE connection before any
     * blocking work begins.
     *
     * @param request        validated chat request containing the user's question
     * @param authentication used to key the per-user rate limit bucket
     * @return an open {@link SseEmitter} that the framework keeps alive until completion
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody @Valid ChatRequest request,
                                 Authentication authentication) {
        rateLimiter.consume(authentication.getName());
        log.info("Chat stream request: question length={}", request.question().length());

        return sseStreamTemplate.stream(writer ->
                ragQueryService.queryStream(request.question(), new RagStreamHandler() {
                    @Override
                    public void onSources(List<RagSource> sources) {
                        writer.send("sources", sources);
                    }

                    @Override
                    public void onToken(String token) {
                        writer.send("token", token);
                    }

                    @Override
                    public void onComplete() {
                        writer.send("done", "[DONE]");
                        writer.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        writer.completeWithError(error);
                    }
                }));
    }
}
