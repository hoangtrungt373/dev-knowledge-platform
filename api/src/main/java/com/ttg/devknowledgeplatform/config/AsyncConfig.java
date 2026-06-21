package com.ttg.devknowledgeplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

/**
 * Async and SSE configuration for the application.
 *
 * <p>Configures a dedicated thread pool for RAG streaming requests so they do not
 * compete with the JVM's shared {@code ForkJoinPool.commonPool()} or other work.
 *
 * <p>Also aligns Spring MVC's async request timeout with the {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}
 * timeout defined in {@link com.ttg.devknowledgeplatform.endpoint.ChatEndpoint},
 * preventing the container from closing the response before the emitter does.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements WebMvcConfigurer {

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
}
