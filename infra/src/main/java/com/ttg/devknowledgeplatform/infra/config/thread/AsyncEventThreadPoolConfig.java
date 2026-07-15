package com.ttg.devknowledgeplatform.infra.config.thread;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Factory for the {@code asyncEventExecutor} bean, the dedicated pool backing every
 * {@code @EventHandler} (application event) dispatch across every module.
 *
 * <p>Moved here from {@code gateway} — this module's own {@link com.ttg.devknowledgeplatform.infra.event.EventHandler}
 * is the thing that actually owns the purpose of this pool (a Decorator-pattern Micrometer
 * instrumentation, same as {@code gateway}'s {@code sseStreamExecutor}), so the bean definition
 * belongs alongside it rather than depending on {@code gateway} to supply it. Kept as a separate
 * bulkhead from {@code gateway}'s {@code sseStreamExecutor}: an SSE stream holds its thread for up
 * to the full 60&nbsp;s request timeout, so a burst of concurrent chat streams must not be able to
 * starve or reject background event handling (e.g. content indexing after a bulk publish) — and
 * conversely, a bulk reindex must not be able to delay chat responses.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AsyncEventThreadPoolConfig {

    private final AsyncEventThreadPoolProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Dedicated executor for {@code @EventHandler} (application event) dispatch.
     *
     * @return fully initialised and instrumented executor
     */
    @Bean(name = "asyncEventExecutor")
    public ThreadPoolTaskExecutor asyncEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("async-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();

        ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.getThreadPoolExecutor(),
                "async-event",
                Tags.empty());

        log.info("Async event executor initialised: core={} max={} queue={}",
                properties.getCorePoolSize(), properties.getMaxPoolSize(), properties.getQueueCapacity());
        return executor;
    }
}
