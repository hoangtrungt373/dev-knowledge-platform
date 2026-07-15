package com.ttg.devknowledgeplatform.config.thread;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised sizing for the {@code sseStreamExecutor} bean.
 *
 * <p>Bound from the {@code app.threads} prefix. Override via environment variables, e.g.
 * {@code APP_THREADS_SSE_EXECUTOR_CORE_POOL_SIZE=20}.
 *
 * <p>The {@code asyncEventExecutor} bulkhead's sizing lives in {@code infra}'s own
 * {@code AsyncEventThreadPoolProperties} instead (prefix {@code app.threads.async-event}) — that
 * module's own {@code EventHandler} framework is the thing that owns its purpose, not this module.
 */
@ConfigurationProperties(prefix = "app.threads")
@Validated
@Getter
@Setter
public class ThreadPoolProperties {

    @Valid
    @NotNull
    private SseExecutor sseExecutor = new SseExecutor();

    /** Sizing parameters for the {@code sseStreamExecutor} bean. */
    @Getter
    @Setter
    public static class SseExecutor {

        /** Threads always kept alive to handle concurrent SSE streams. */
        @Positive
        private int corePoolSize = 10;

        /** Hard upper bound on threads under burst load. */
        @Positive
        private int maxPoolSize = 50;

        /** Requests wait here before a new thread is spawned above {@code corePoolSize}. */
        @Positive
        private int queueCapacity = 100;

        /** Seconds the pool waits for active streams to finish on graceful shutdown. */
        @Positive
        private int awaitTerminationSeconds = 30;
    }
}
