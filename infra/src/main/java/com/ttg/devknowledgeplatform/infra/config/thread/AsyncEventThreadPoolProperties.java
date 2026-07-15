package com.ttg.devknowledgeplatform.infra.config.thread;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalised sizing for the {@code asyncEventExecutor} bean, which backs every
 * {@code @EventHandler} (application event) dispatch.
 *
 * <p>Bound from the {@code app.threads.async-event} prefix. Kept separate from {@code gateway}'s
 * own {@code sseExecutor} sizing (a different bulkhead) so a burst of background event handling
 * (e.g. bulk content indexing) cannot starve or reject user-facing SSE streams, and vice versa —
 * moved here alongside {@link AsyncEventThreadPoolConfig} since this module's own
 * {@code EventHandler}/{@code AsyncEventHandler} framework is the thing that owns this pool's
 * purpose, not {@code gateway}.
 */
@ConfigurationProperties(prefix = "app.threads.async-event")
@Validated
@Getter
@Setter
public class AsyncEventThreadPoolProperties {

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
