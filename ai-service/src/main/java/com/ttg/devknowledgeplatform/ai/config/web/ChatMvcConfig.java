package com.ttg.devknowledgeplatform.ai.config.web;

import java.util.List;

import com.ttg.devknowledgeplatform.ai.config.sse.SseStreamTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This module's own Spring MVC configuration — used to be composed alongside {@code gateway}'s own
 * {@code WebMvcConfig}, which is now deleted: both of its responsibilities
 * ({@code configureAsyncSupport} for {@code sseStreamExecutor}, {@code addArgumentResolvers} for
 * {@code @CurrentUserId}) only ever served this module.
 *
 * <p>Registers the chat rate-limit interceptor for all {@code /api/v1/chat/**} paths (the
 * interceptor itself skips non-POST requests so GET session endpoints are unaffected), this
 * module's own {@link CurrentUserIdArgumentResolver} (resolves {@code @CurrentUserId String}), and
 * the SSE stream executor as Spring MVC's async task executor + timeout (the timeout value is
 * owned by {@link SseStreamTemplate#SSE_TIMEOUT_MS}, not duplicated here, since that class also
 * constructs {@code SseEmitter} instances directly and the two timeouts must stay in sync).
 */
@Configuration
@RequiredArgsConstructor
public class ChatMvcConfig implements WebMvcConfigurer {

    private final ChatRateLimitInterceptor chatRateLimitInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final ThreadPoolTaskExecutor sseStreamExecutor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(chatRateLimitInterceptor)
                .addPathPatterns("/api/v1/chat/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SseStreamTemplate.SSE_TIMEOUT_MS);
        configurer.setTaskExecutor(sseStreamExecutor);
    }
}
