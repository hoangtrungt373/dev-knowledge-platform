package com.ttg.devknowledgeplatform.config.sse;

import com.ttg.devknowledgeplatform.config.web.WebMvcConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reusable template for SSE endpoints.
 *
 * <p>Handles all SSE infrastructure so individual endpoints only need to describe
 * what events to send, not how to manage the connection:
 *
 * <ul>
 *   <li>Creates the {@link SseEmitter} with the configured timeout.</li>
 *   <li>Registers {@code onCompletion / onTimeout / onError} lifecycle callbacks.</li>
 *   <li>Offloads work to the dedicated {@code ragStreamExecutor} thread pool.</li>
 *   <li>Wraps the emitter in an {@link SseEmitterWriter} that guards every write.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * return sseStreamTemplate.stream(writer -> {
 *     someService.runWithCallbacks(new Handler() {
 *         public void onData(Object d)  { writer.send("data", d); }
 *         public void onDone()          { writer.send("done", "[DONE]"); writer.complete(); }
 *         public void onError(Throwable e) { writer.completeWithError(e); }
 *     });
 * });
 * }</pre>
 */
@Component
@Slf4j
public class SseStreamTemplate {

    private final Executor executor;

    public SseStreamTemplate(@Qualifier("sseStreamExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * Creates an open SSE connection, registers lifecycle callbacks, and runs
     * {@code work} on the dedicated thread pool.
     *
     * @param work lambda that uses the provided {@link SseEmitterWriter} to send events;
     *             any uncaught exception is forwarded to {@link SseEmitterWriter#completeWithError}
     * @return the open {@link SseEmitter} — returned immediately to the HTTP layer
     *         before {@code work} begins executing
     */
    public SseEmitter stream(Consumer<SseEmitterWriter> work) {
        SseEmitter emitter = new SseEmitter(WebMvcConfig.SSE_TIMEOUT_MS);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        emitter.onCompletion(() -> {
            cancelled.set(true);
            log.debug("SSE stream completed");
        });
        emitter.onTimeout(() -> {
            cancelled.set(true);
            log.warn("SSE stream timed out");
        });
        emitter.onError(e -> {
            cancelled.set(true);
            log.warn("SSE stream error: {}", e.getMessage());
        });

        SseEmitterWriter writer = new SseEmitterWriter(emitter, cancelled);

        CompletableFuture.runAsync(() -> {
            try {
                work.accept(writer);
            } catch (Exception e) {
                log.error("Unhandled exception in SSE stream work: {}", e.getMessage(), e);
                writer.completeWithError(e);
            }
        }, executor);

        return emitter;
    }
}
