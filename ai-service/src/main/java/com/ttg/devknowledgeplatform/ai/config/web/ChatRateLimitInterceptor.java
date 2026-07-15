package com.ttg.devknowledgeplatform.ai.config.web;

import com.ttg.devknowledgeplatform.ai.config.chat.ChatRateLimiter;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies per-user rate limiting to all POST requests on the chat API.
 *
 * <p>Runs in the Spring MVC filter chain before the controller method is invoked,
 * so no endpoint ever needs to call the rate limiter manually. GET requests (session
 * history, session list) are passed through without consuming a token.
 *
 * <p>If the limit is exceeded, {@link ChatRateLimiter#consume} throws
 * {@link com.ttg.devknowledgeplatform.common.exception.RateLimitExceededException},
 * which {@code GlobalExceptionHandler} maps to {@code 429 Too Many Requests}.
 *
 * <p>Registered via {@link ChatMvcConfig} for {@code /api/v1/chat/**}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRateLimitInterceptor implements HandlerInterceptor {

    private final ChatRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true;  // unauthenticated requests are blocked earlier by SecurityConfig
        }

        CustomOAuth2User principal = (CustomOAuth2User) auth.getPrincipal();
        log.debug("Rate limit check: userUuid={}", principal.getUserUuid());
        rateLimiter.consume(principal.getUserUuid());
        return true;
    }
}
