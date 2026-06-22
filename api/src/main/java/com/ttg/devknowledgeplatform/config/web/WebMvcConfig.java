package com.ttg.devknowledgeplatform.config.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Central Spring MVC configuration for the application.
 *
 * <p>Implements {@link WebMvcConfigurer} to customise the DispatcherServlet layer:
 * <ul>
 *   <li>Registers the SSE streaming executor and aligns the async request timeout.</li>
 *   <li>Registers the chat rate-limit interceptor.</li>
 *   <li>Registers the {@link CurrentUserIdArgumentResolver} for {@code @CurrentUserId} parameters.</li>
 * </ul>
 *
 * <p>{@code @EnableAsync} is declared here so that the {@code sseStreamExecutor} bean
 * is available as both the MVC async executor (via {@link #configureAsyncSupport}) and
 * the Spring {@code @Async} executor.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final ChatRateLimitInterceptor chatRateLimitInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    /**
     * Timeout in milliseconds applied to both Spring MVC async requests and
     * {@code SseEmitter} instances — must be kept in sync.
     */
    public static final long SSE_TIMEOUT_MS = 60_000L;

    /**
     * Dedicated executor for all SSE streaming tasks.
     *
     * <p>Sizing rationale:
     * <ul>
     *   <li>{@code corePoolSize=10} — threads always ready for concurrent streams.</li>
     *   <li>{@code maxPoolSize=50} — upper bound under burst load.</li>
     *   <li>{@code queueCapacity=100} — requests wait here before a new thread is spawned.</li>
     * </ul>
     *
     * <p>Shutdown is graceful: active streams are allowed up to 30 seconds to finish
     * before the application exits.
     */
    @Bean(name = "sseStreamExecutor")
    public Executor sseStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("SSE stream executor initialised: core={} max={} queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }

    /**
     * Aligns the Spring MVC async request timeout with {@link #SSE_TIMEOUT_MS} and
     * sets the SSE stream executor as the default task executor for async requests.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SSE_TIMEOUT_MS);
        configurer.setTaskExecutor((org.springframework.core.task.AsyncTaskExecutor) sseStreamExecutor());
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
