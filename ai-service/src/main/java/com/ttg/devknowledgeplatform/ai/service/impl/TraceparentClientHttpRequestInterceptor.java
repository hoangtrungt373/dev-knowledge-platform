package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.infra.context.MdcKeys;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContext;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Stamps a {@code traceparent} header — carrying this thread's own current span as the outgoing
 * {@code parent-id}, exactly the way {@code infra}'s {@code tracing.TraceContextFilter} does for
 * an inbound-triggered outbound hop — onto every {@link ContentServiceClientImpl} call.
 *
 * <p>Deliberately forwards the current span as-is rather than minting a further child span for
 * the outbound call itself: this reactor's tracing is correlation-focused, not a full tracing SDK,
 * and treating "this app's handling of the request" and "the outbound call it makes as part of
 * that handling" as one span keeps this interceptor's behavior consistent with
 * {@code TraceContextFilter}'s own (which does the same for every proxied {@code gateway} route).
 * The receiving service (here, {@code content-service}) mints its own new span from whatever it
 * receives, same as every other hop.
 *
 * <p><b>Known gap, by design:</b> when this call happens on the background thread driving
 * {@code ai-service}'s async indexing pipeline (see {@code ai-service/CLAUDE.md}'s "Async" note),
 * MDC will typically be empty — {@code @Async} does not copy the triggering request's MDC onto the
 * worker thread — so this interceptor falls back to {@link TraceContext#fresh()}, starting a new,
 * disconnected trace rather than one linked back to whatever admin request kicked off indexing.
 * Fixing that means wiring an MDC-propagating {@code TaskDecorator} onto that specific executor —
 * a separate piece of work, not solved here.
 */
class TraceparentClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(TraceContext.HEADER_NAME, currentOrFresh().toHeaderValue());
        return execution.execute(request, body);
    }

    private TraceContext currentOrFresh() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        if (traceId == null || spanId == null) {
            return TraceContext.fresh();
        }
        return new TraceContext(traceId, spanId, true);
    }
}
