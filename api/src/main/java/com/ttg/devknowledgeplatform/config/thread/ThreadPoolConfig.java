package com.ttg.devknowledgeplatform.config.thread;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Factory for all application-managed thread pools.
 *
 * <p><strong>Pattern — Factory Method (GoF Creational):</strong> each {@code @Bean} method is
 * a named factory that constructs, configures, and registers one pool. Callers receive a
 * ready-to-use executor without knowing its construction details; sizing comes from
 * {@link ThreadPoolProperties} so tuning requires only a config change, not a recompile.
 *
 * <p><strong>Pattern — Decorator (GoF Structural):</strong> each pool is registered with
 * Micrometer via {@link ExecutorServiceMetrics}, which wraps the real executor with
 * instrumentation. The instrumented metrics are exposed at {@code /actuator/metrics} under the
 * prefixes {@code executor.active}, {@code executor.pool.size}, {@code executor.queued}, and
 * {@code executor.completed}, tagged by pool name.
 *
 * <p>Current pools:
 * <ul>
 *   <li><strong>sseStreamExecutor</strong> — handles all SSE streaming requests; used by
 *       Spring MVC async support ({@code configureAsyncSupport}) and by
 *       {@link com.ttg.devknowledgeplatform.config.sse.SseStreamTemplate}.</li>
 *   <li><strong>asyncEventExecutor</strong> — dedicated pool for {@code @EventHandler}
 *       (application event) dispatch. Kept separate from {@code sseStreamExecutor} as a
 *       bulkhead: an SSE stream holds its thread for up to the full 60&nbsp;s request timeout,
 *       so a burst of concurrent chat streams must not be able to starve or reject background
 *       event handling (e.g. content indexing after a bulk publish) — and conversely, a bulk
 *       reindex must not be able to delay chat responses.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ThreadPoolConfig {

    private final ThreadPoolProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Dedicated executor for all SSE streaming tasks.
     *
     * <p>Sizing rationale:
     * <ul>
     *   <li>{@code corePoolSize} — threads always ready for concurrent streams.</li>
     *   <li>{@code maxPoolSize} — hard upper bound under burst load.</li>
     *   <li>{@code queueCapacity} — requests wait here before a new thread is spawned
     *       above {@code corePoolSize}.</li>
     * </ul>
     *
     * <p>Shutdown is graceful: active streams are allowed up to
     * {@link ThreadPoolProperties.SseExecutor#getAwaitTerminationSeconds()} seconds to finish
     * before the application exits.
     *
     * <p>Metrics are published at:
     * <ul>
     *   <li>{@code executor.active{name="sse-stream"}}</li>
     *   <li>{@code executor.pool.size{name="sse-stream"}}</li>
     *   <li>{@code executor.queued{name="sse-stream"}}</li>
     *   <li>{@code executor.completed{name="sse-stream"}}</li>
     * </ul>
     *
     * @return fully initialised and instrumented executor
     */
    @Bean(name = "sseStreamExecutor")
    public ThreadPoolTaskExecutor sseStreamExecutor() {
        ThreadPoolProperties.SseExecutor cfg = properties.getSseExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("sse-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(cfg.getAwaitTerminationSeconds());
        executor.initialize();

        // Decorator: bind Micrometer instrumentation after initialize() so
        // getThreadPoolExecutor() is available.
        ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "sse-stream",
                Tags.empty());

        log.info("SSE stream executor initialised: core={} max={} queue={}",
                cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity());
        return executor;
    }

    /**
     * Dedicated executor for {@code @EventHandler} (application event) dispatch.
     *
     * <p>Isolated from {@code sseStreamExecutor} so the two workloads cannot contend for the
     * same threads — see the bulkhead rationale on the class-level Javadoc. Sizing rationale
     * mirrors {@link #sseStreamExecutor()}; {@code queueCapacity} is larger here because a
     * queued event handler only delays a background task rather than a live HTTP response.
     *
     * @return fully initialised and instrumented executor
     */
    @Bean(name = "asyncEventExecutor")
    public ThreadPoolTaskExecutor asyncEventExecutor() {
        ThreadPoolProperties.AsyncEventExecutor cfg = properties.getAsyncEventExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("async-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(cfg.getAwaitTerminationSeconds());
        executor.initialize();

        ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "async-event",
                Tags.empty());

        log.info("Async event executor initialised: core={} max={} queue={}",
                cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity());
        return executor;
    }
}
