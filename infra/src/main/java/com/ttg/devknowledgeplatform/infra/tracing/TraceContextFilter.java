package com.ttg.devknowledgeplatform.infra.tracing;

import com.ttg.devknowledgeplatform.infra.context.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Binds a {@link TraceContext} to every inbound HTTP request in whichever of this reactor's seven
 * Spring Boot apps this filter runs in (registered automatically — see "Registration" below), and
 * emits one structured access-log line per request.
 *
 * <h3>What it does, per request</h3>
 * <ol>
 *   <li>Reads the inbound {@value TraceContext#HEADER_NAME} header. If present and well-formed,
 *       derives this app's own span via {@link TraceContext#withNewSpan()} — same trace-id, a new
 *       span-id representing this app's own handling. If absent or malformed, starts a brand-new
 *       trace via {@link TraceContext#fresh()} (this app is the first hop — e.g. a backend service
 *       reached directly, bypassing {@code gateway}).</li>
 *   <li>Binds {@link MdcKeys#TRACE_ID}/{@link MdcKeys#SPAN_ID} to MDC for the duration of the
 *       request, so every log line this app writes while handling it carries both automatically
 *       (see each app's own {@code logging.pattern.console} — updated everywhere this filter runs
 *       to render {@code %X{traceId}}/{@code %X{spanId}}, since an MDC value a log pattern never
 *       references is silently invisible).</li>
 *   <li>Wraps the request so the {@value TraceContext#HEADER_NAME} header this app's own outbound
 *       code sees — Gateway Server MVC's proxy dispatch reading it to forward downstream, or a
 *       {@code RestClient}/{@code HttpClient} call reading it explicitly — is this app's own
 *       {@link TraceContext#withNewSpan() span}, not whatever arrived on the inbound request. This
 *       is what makes the receiving service's next hop see *this* app's span as its
 *       {@code parent-id}, not the span of whoever called *this* app.</li>
 *   <li>After the request completes, logs one structured access-log line — method, path, status,
 *       latency — then removes both MDC keys so a servlet-container thread reused for the next,
 *       unrelated request doesn't inherit this one's trace.</li>
 * </ol>
 *
 * <h3>Registration</h3>
 * <p>A plain {@code @Component} implementing {@code Filter} (via {@link OncePerRequestFilter}) is
 * auto-registered as a servlet filter by Spring Boot for any app whose component scan reaches this
 * class — which, since the reactor-wide {@code @ComponentScan} fix (see root {@code CLAUDE.md}),
 * is all seven apps (all six standalone services plus {@code gateway}). No per-app wiring needed
 * beyond that scan already reaching {@code infra}.
 *
 * <h3>How the header actually reaches a downstream service</h3>
 * <p>This filter never calls into {@code gateway}'s own {@code routing/GatewayRoutesConfig} or its
 * proxy dispatch — it only rewrites the <em>inbound</em> {@link HttpServletRequest} it hands to the
 * rest of the filter chain. Spring Cloud Gateway Server MVC's {@code HandlerFunctions.http(...)}
 * forwards a proxied request's headers by default (aside from a short, explicitly "sensitive" list
 * — {@code cookie}/{@code authorization} — and this reactor already relies on {@code authorization}
 * reaching every backend service's own JWT verification regardless, so headers do pass through in
 * this deployment); {@code traceparent} is not on that sensitive list, so the rewritten value this
 * filter installs on the wrapped request is what the proxy ends up forwarding downstream, with no
 * change needed to {@code GatewayRoutesConfig} itself. {@code gateway}'s hand-rolled
 * {@code routing/ChatStreamProxyController} (which bypasses that proxy DSL entirely for SSE) reads
 * the header explicitly via its own {@code @RequestHeader} parameter instead, the same way it
 * already forwards {@code Authorization}/{@code Content-Type}/{@code Accept}.
 *
 * <h3>What this filter does <em>not</em> do</h3>
 * <p>It does not propagate the trace across an {@code @Async} boundary — MDC is thread-local, and
 * Spring's default {@code @Async} executor does not copy the calling thread's MDC onto the worker
 * thread. {@code ai-service}'s own background indexing pipeline (triggered by an admin request,
 * then continuing on a separate thread) is the one place in this reactor this matters — its own
 * outbound call to {@code content-service} reads whatever MDC exists on <em>its</em> thread, which
 * will usually be empty and fall back to a fresh, disconnected trace rather than one linked to the
 * admin request that triggered it. See {@code ai-service}'s own {@code CLAUDE.md} for this known,
 * deliberately out-of-scope gap — fixing it means wiring an MDC-propagating {@code TaskDecorator}
 * onto that specific executor, a separate piece of work.
 */
@Component
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        TraceContext mySpan = TraceContext.parse(request.getHeader(TraceContext.HEADER_NAME))
                .map(TraceContext::withNewSpan)
                .orElseGet(TraceContext::fresh);

        MDC.put(MdcKeys.TRACE_ID, mySpan.traceId());
        MDC.put(MdcKeys.SPAN_ID, mySpan.spanId());

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(
                    new TraceparentRewritingRequestWrapper(request, mySpan.toHeaderValue()), response);
        } finally {
            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("method={} path={} status={} tookMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), tookMs);
            MDC.remove(MdcKeys.TRACE_ID);
            MDC.remove(MdcKeys.SPAN_ID);
        }
    }

    /**
     * Makes the {@value TraceContext#HEADER_NAME} header this app's own outbound code sees equal
     * to this app's own span, regardless of what (if anything) arrived on the real inbound
     * request. Every other header passes through to the wrapped {@link HttpServletRequest}
     * unchanged.
     */
    private static final class TraceparentRewritingRequestWrapper extends HttpServletRequestWrapper {

        private final String rewrittenValue;

        TraceparentRewritingRequestWrapper(HttpServletRequest request, String rewrittenValue) {
            super(request);
            this.rewrittenValue = rewrittenValue;
        }

        @Override
        public String getHeader(String name) {
            if (TraceContext.HEADER_NAME.equalsIgnoreCase(name)) {
                return rewrittenValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (TraceContext.HEADER_NAME.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(rewrittenValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            boolean hadTraceparent = false;
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                names.add(name);
                if (TraceContext.HEADER_NAME.equalsIgnoreCase(name)) {
                    hadTraceparent = true;
                }
            }
            if (!hadTraceparent) {
                // The original request may not have carried a traceparent header at all (this
                // app is the first hop) — still advertise it so code iterating getHeaderNames()
                // finds it, not just code calling getHeader(...) directly by name.
                names.add(TraceContext.HEADER_NAME);
            }
            return Collections.enumeration(names);
        }
    }
}
