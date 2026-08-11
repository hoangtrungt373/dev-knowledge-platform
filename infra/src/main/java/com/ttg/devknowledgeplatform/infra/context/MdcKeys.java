package com.ttg.devknowledgeplatform.infra.context;

/**
 * MDC key constants shared across all modules.
 *
 * <p>Centralising these strings prevents magic-string duplication between
 * {@code ai-service} and {@code api} and ensures the logging pattern in
 * {@code logback-spring.xml} stays in sync with the keys actually written to MDC.
 *
 * <p>Keys written here correspond directly to the {@code %X{key}} placeholders
 * in the log pattern. Adding a new key here is the first step when wiring a new
 * piece of context into the log output.
 */
public final class MdcKeys {

    /**
     * Per-request trace identifier — the W3C Trace Context {@code trace-id}. Set by
     * {@link com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler} for async event
     * dispatch, and by {@link com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter} for
     * every inbound HTTP request in every one of this reactor's seven Spring Boot apps.
     */
    public static final String TRACE_ID = "traceId";

    /**
     * This app's own span within the current trace — the W3C Trace Context {@code parent-id} this
     * app mints for itself, distinct from the {@code parent-id} it *received* on the inbound
     * request (which identifies the caller's span, not this app's own). Set by
     * {@link com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter}. Two different
     * services handling legs of the same trace share {@link #TRACE_ID} but never
     * {@code SPAN_ID} — that's what lets a shared trace-id search still tell each hop's own log
     * lines apart.
     */
    public static final String SPAN_ID = "spanId";

    private MdcKeys() {
    }
}
