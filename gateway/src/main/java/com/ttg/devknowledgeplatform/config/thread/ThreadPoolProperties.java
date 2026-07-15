package com.ttg.devknowledgeplatform.config.thread;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised sizing for all application-managed thread pools.
 *
 * <p>Bound from the {@code app.threads} prefix. Override via environment variables, e.g.
 * {@code APP_THREADS_SSE_EXECUTOR_CORE_POOL_SIZE=20}.
 *
 * <p>Having all pool sizes in one place means a capacity review only requires reading this
 * config — no grep through {@code @Bean} methods is needed.
 */
@ConfigurationProperties(prefix = "app.threads")
@Validated
@Getter
@Setter
public class ThreadPoolProperties {

    @Valid
    @NotNull
    private SseExecutor sseExecutor = new SseExecutor();

    @Valid
    @NotNull
    private AsyncEventExecutor asyncEventExecutor = new AsyncEventExecutor();

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

    /**
     * Sizing parameters for the {@code asyncEventExecutor} bean, which backs every
     * {@code @EventHandler} (application event) dispatch — kept separate from
     * {@code sseExecutor} so a burst of background event handling (e.g. bulk content
     * indexing) cannot starve or reject user-facing SSE streams, and vice versa.
     */
    @Getter
    @Setter
    public static class AsyncEventExecutor {

        /** Threads always kept alive to handle event dispatch. */
        @Positive
        private int corePoolSize = 5;

        /** Hard upper bound on threads under burst load (e.g. bulk reindex). */
        @Positive
        private int maxPoolSize = 20;

        /**
         * Requests wait here before a new thread is spawned above {@code corePoolSize}.
         * Sized larger than the SSE queue since a queued event handler only delays a
         * background task, not a user-facing response.
         */
        @Positive
        private int queueCapacity = 200;

        /** Seconds the pool waits for in-flight handlers to finish on graceful shutdown. */
        @Positive
        private int awaitTerminationSeconds = 30;
    }
}
