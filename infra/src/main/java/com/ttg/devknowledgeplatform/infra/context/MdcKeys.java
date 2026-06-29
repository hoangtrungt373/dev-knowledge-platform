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

    /** Per-request trace identifier set by {@link com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler}. */
    public static final String TRACE_ID = "traceId";

    private MdcKeys() {
    }
}
