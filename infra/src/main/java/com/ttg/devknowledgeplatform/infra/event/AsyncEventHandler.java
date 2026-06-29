package com.ttg.devknowledgeplatform.infra.event;

import com.ttg.devknowledgeplatform.infra.context.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Base class for asynchronous Spring application event handlers (Template Method pattern).
 *
 * <h3>Template Method</h3>
 * <p>{@link #handle(Object)} is the fixed algorithm skeleton — it runs on a background thread
 * via {@link EventHandler}, sets up MDC context, times execution, delegates to
 * {@link #doHandle(Object)}, and guarantees exception safety. Subclasses implement only
 * {@code doHandle()}, the variable step.
 *
 * <h3>Cross-cutting concerns provided for free</h3>
 * <ul>
 *   <li><strong>Async dispatch</strong> — {@code @EventHandler} composes
 *       {@code @EventListener + @Async}; the handler always runs off the publishing thread.</li>
 *   <li><strong>MDC trace propagation</strong> — if {@link #resolveTraceId(Object)} returns a
 *       non-null value, it is bound to {@code MDC[}{@link MdcKeys#TRACE_ID}{@code ]} for the
 *       duration of the handler so every log line emitted inside {@code doHandle()} carries the
 *       trace ID automatically.</li>
 *   <li><strong>Timing</strong> — wall-clock duration is logged at DEBUG level after
 *       {@code doHandle()} completes.</li>
 *   <li><strong>Exception safety</strong> — any unchecked or checked exception thrown by
 *       {@code doHandle()} is caught and logged at ERROR level; it is never rethrown so that
 *       a handler failure cannot crash the event bus or stall the publishing thread.</li>
 * </ul>
 *
 * <h3>Transaction handling</h3>
 * <p>This class is intentionally not annotated with {@code @Transactional}. Subclasses that
 * write to the database should annotate their own class with {@code @Transactional} — Spring
 * will proxy the concrete bean and the transaction will wrap the inherited
 * {@link #handle(Object)} entry point correctly.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Service
 * @WriteTransactional     // optional: only if the handler writes to the DB
 * public class MyHandler extends AsyncEventHandler<MyEvent> {
 *
 *     @Override
 *     protected String resolveTraceId(MyEvent event) {
 *         return event.traceId();   // optional: omit to skip MDC binding
 *     }
 *
 *     @Override
 *     protected void doHandle(MyEvent event) throws Exception {
 *         // handler logic — no try/catch, no @Async, no @EventListener needed
 *     }
 * }
 * }</pre>
 *
 * @param <E> the application event type this handler listens for; resolved from the concrete
 *            subclass at runtime via Spring's {@code ResolvableType}
 * @see EventHandler
 * @see ApplicationEventHandler
 */
public abstract class AsyncEventHandler<E> implements ApplicationEventHandler {

    /** Logger named after the concrete subclass, not the abstract base. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Entry point invoked by the Spring event bus.
     *
     * <p>This method is {@code final} to enforce the Template Method contract — all handler
     * logic must go in {@link #doHandle(Object)}.
     *
     * @param event the published event; never {@code null}
     */
    @EventHandler
    public final void handle(E event) {
        String traceId = resolveTraceId(event);
        if (traceId != null) {
            MDC.put(MdcKeys.TRACE_ID, traceId);
        }
        long start = System.currentTimeMillis();
        try {
            log.debug("Handling {}", event.getClass().getSimpleName());
            doHandle(event);
            log.debug("Handled {} in {}ms", event.getClass().getSimpleName(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Handler {} failed for event {} — {}",
                    getClass().getSimpleName(), event.getClass().getSimpleName(), e.getMessage(), e);
        } finally {
            if (traceId != null) {
                MDC.remove(MdcKeys.TRACE_ID);
            }
        }
    }

    /**
     * Implements the event handling logic.
     *
     * <p>Runs on a background thread inside the lifecycle managed by {@link #handle(Object)}.
     * Uncaught exceptions are logged by the base class — no {@code try/catch} needed here.
     * Do not add {@code @EventListener} or {@code @Async} — both are provided by the base class.
     *
     * @param event the published event
     * @throws Exception any exception; caught and logged by {@link #handle(Object)}
     */
    protected abstract void doHandle(E event) throws Exception;

    /**
     * Extracts a trace ID from the event to bind to {@link MdcKeys#TRACE_ID} in MDC
     * for the duration of this handler invocation.
     *
     * <p>The default returns {@code null} (no MDC binding). Override when the event type
     * carries a trace identifier so that every log line inside {@link #doHandle(Object)} is
     * automatically correlated without manual MDC calls.
     *
     * @param event the published event
     * @return the trace ID string, or {@code null} to skip MDC binding
     */
    protected String resolveTraceId(E event) {
        return null;
    }
}
