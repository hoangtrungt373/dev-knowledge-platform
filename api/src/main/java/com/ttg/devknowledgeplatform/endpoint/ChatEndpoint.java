package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.config.ChatRateLimiter;
import com.ttg.devknowledgeplatform.dto.chat.ChatRequest;
import com.ttg.devknowledgeplatform.dto.chat.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
