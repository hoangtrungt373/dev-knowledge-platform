package com.ttg.devknowledgeplatform.config.web;

import com.ttg.devknowledgeplatform.ai.config.sse.SseStreamTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Central Spring MVC configuration for the application.
 *
 * <p>Implements {@link WebMvcConfigurer} to customise the DispatcherServlet layer:
 * <ul>
 *   <li>Wires the SSE streaming executor (created by {@code ThreadPoolConfig}) as the
 *       default async task executor and aligns the async request timeout.</li>
 *   <li>Registers the {@link CurrentUserIdArgumentResolver} for {@code @CurrentUserId} parameters.</li>
 * </ul>
 *
 * <p>Interceptor registration is deliberately NOT centralized here — {@code ai-service}'s own
 * {@code ChatMvcConfig} registers the chat rate-limit interceptor directly; Spring composes every
 * {@link WebMvcConfigurer} bean in the context automatically, so each module can own its own
 * endpoint-specific MVC customization without needing this class to do it on their behalf.
 *
 * <p>{@code @EnableAsync} is declared here to activate Spring's {@code @Async} support.
 * {@code sseStreamExecutor} is wired only into Spring MVC's async request dispatch below;
 * {@code @Async} method dispatch uses its own explicitly-qualified pool
 * ({@code asyncEventExecutor}, see {@code com.ttg.devknowledgeplatform.infra.event.EventHandler})
 * so the two workloads never contend for the same threads.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final ThreadPoolTaskExecutor sseStreamExecutor;

    /**
     * Registers the SSE stream executor as the default async task executor and sets the
     * request timeout. The executor bean is created and instrumented by {@code ThreadPoolConfig}.
     *
     * <p>The timeout value itself is owned by {@link SseStreamTemplate#SSE_TIMEOUT_MS}
     * (ai-service) — not duplicated here — since that class also constructs {@code SseEmitter}
     * instances directly and the two timeouts must stay in sync.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SseStreamTemplate.SSE_TIMEOUT_MS);
        configurer.setTaskExecutor(sseStreamExecutor);
    }

    /**
     * Registers {@link CurrentUserIdArgumentResolver} so any controller parameter annotated
     * with {@link com.ttg.devknowledgeplatform.common.annotation.CurrentUserId} is automatically
     * resolved to the authenticated user's integer primary key — no manual
     * {@code userRepository.findByUserUuid(principal.getUserUuid())} lookups required in
     * every controller method.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
