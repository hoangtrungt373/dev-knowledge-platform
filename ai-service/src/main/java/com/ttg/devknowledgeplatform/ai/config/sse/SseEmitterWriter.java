package com.ttg.devknowledgeplatform.ai.config.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around {@link SseEmitter} that centralises the three concerns
 * every SSE send must handle:
 *
 * <ul>
 *   <li>Guard — skip the write if the client already disconnected.</li>
 *   <li>IOException — mark cancelled and close the emitter if the write fails mid-stream.</li>
 *   <li>Double-complete — ignore {@code complete/completeWithError} calls after the
 *       connection is already closed.</li>
 * </ul>
 *
 * <p>Instances are created by {@link SseStreamTemplate} and passed to the work lambda.
 * Callers should never hold a reference to the underlying {@link SseEmitter} directly.
 */
@Slf4j
public class SseEmitterWriter {

    private final SseEmitter emitter;
    private final AtomicBoolean cancelled;

    SseEmitterWriter(SseEmitter emitter, AtomicBoolean cancelled) {
        this.emitter = emitter;
        this.cancelled = cancelled;
    }

    /**
     * Sends one SSE event if the connection is still alive.
     *
     * @param eventName the {@code event:} field sent to the client
     * @param data      serialised as JSON by Spring's message converters
     */
    public void send(String eventName, Object data) {
        if (cancelled.get()) return;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.debug("SSE write failed (client likely disconnected): {}", e.getMessage());
            cancelled.set(true);
            emitter.completeWithError(e);
        }
    }

    /** Closes the SSE connection cleanly. No-op if already cancelled. */
    public void complete() {
        if (!cancelled.get()) {
            emitter.complete();
        }
    }

    /** Closes the SSE connection with an error. No-op if already cancelled. */
    public void completeWithError(Throwable error) {
        if (!cancelled.getAndSet(true)) {
            emitter.completeWithError(error);
        }
    }

    /** Returns {@code true} if the client disconnected or the stream timed out. */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
