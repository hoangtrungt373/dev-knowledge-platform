package com.ttg.devknowledgeplatform.ai.config.thread;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Factory for the {@code sseStreamExecutor} bean.
 *
 * <p>Moved here from {@code gateway}'s class of the same name — this module (its
 * {@code ChatController}/{@code SseStreamTemplate}, plus Spring MVC's async dispatch via
 * {@code ChatMvcConfig.configureAsyncSupport}) was always the only real consumer.
 *
 * <p><strong>Pattern — Factory Method (GoF Creational):</strong> constructs, configures, and
 * registers the pool. Callers receive a ready-to-use executor without knowing its construction
 * details; sizing comes from {@link ThreadPoolProperties} so tuning requires only a config change,
 * not a recompile.
 *
 * <p><strong>Pattern — Decorator (GoF Structural):</strong> the pool is registered with Micrometer
 * via {@link ExecutorServiceMetrics}, which wraps the real executor with instrumentation. The
 * instrumented metrics are exposed at {@code /actuator/metrics} under the prefixes
 * {@code executor.active}, {@code executor.pool.size}, {@code executor.queued}, and
 * {@code executor.completed}, tagged by pool name.
 *
 * <p>The {@code asyncEventExecutor} bulkhead (dedicated pool for {@code @EventHandler} dispatch,
 * kept separate so a burst of concurrent SSE streams can't starve background event handling and
 * vice versa) lives in {@code infra}'s own {@code AsyncEventThreadPoolConfig} — that module's own
 * event framework owns its purpose.
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
}
