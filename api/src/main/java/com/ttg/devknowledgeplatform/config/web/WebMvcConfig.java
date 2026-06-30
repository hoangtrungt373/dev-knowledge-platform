package com.ttg.devknowledgeplatform.config.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Central Spring MVC configuration for the application.
 *
 * <p>Implements {@link WebMvcConfigurer} to customise the DispatcherServlet layer:
 * <ul>
 *   <li>Wires the SSE streaming executor (created by {@code ThreadPoolConfig}) as the
 *       default async task executor and aligns the async request timeout.</li>
 *   <li>Registers the chat rate-limit interceptor.</li>
 *   <li>Registers the {@link CurrentUserIdArgumentResolver} for {@code @CurrentUserId} parameters.</li>
 * </ul>
 *
 * <p>{@code @EnableAsync} is declared here so that the injected {@code sseStreamExecutor} bean
 * is also used as the Spring {@code @Async} default executor.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final ChatRateLimitInterceptor chatRateLimitInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final ThreadPoolTaskExecutor sseStreamExecutor;

    /**
     * Timeout in milliseconds applied to both Spring MVC async requests and
     * {@code SseEmitter} instances — must be kept in sync.
     */
    public static final long SSE_TIMEOUT_MS = 60_000L;

    /**
     * Registers the SSE stream executor as the default async task executor and sets the
     * request timeout. The executor bean is created and instrumented by {@code ThreadPoolConfig}.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SSE_TIMEOUT_MS);
        configurer.setTaskExecutor(sseStreamExecutor);
    }

    /**
     * Registers the chat rate-limit interceptor for all {@code /api/v1/chat/**} paths.
     * The interceptor itself skips non-POST requests so GET session endpoints are unaffected.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(chatRateLimitInterceptor)
                .addPathPatterns("/api/v1/chat/**");
    }

    /**
     * Registers {@link CurrentUserIdArgumentResolver} so any controller parameter annotated
     * with {@link com.ttg.devknowledgeplatform.annotation.CurrentUserId} is automatically
     * resolved to the authenticated user's integer primary key — no manual
     * {@code Integer.parseInt(principal.getId())} calls required.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
