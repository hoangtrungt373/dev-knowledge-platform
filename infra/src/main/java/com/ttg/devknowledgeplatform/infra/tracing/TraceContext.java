package com.ttg.devknowledgeplatform.infra.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A single leg of a distributed trace, in the shape defined by the
 * <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a> {@code traceparent} header:
 * {@code version-trace_id-parent_id-flags}. Only version {@code 00} — the only version defined by
 * the spec today — is supported; anything else is treated as unparseable so the caller falls back
 * to {@link #fresh()} rather than guessing at a future version's field semantics.
 *
 * <p>{@code traceId} stays identical for every hop of one distributed call chain — it is what a
 * human searches logs across every one of this reactor's seven Spring Boot apps for. {@code spanId}
 * identifies one single hop's own local processing and is <strong>not</strong> the same value
 * received on the inbound request: see {@link #withNewSpan()}.
 *
 * <p>Deliberately does not implement sampling logic beyond always setting the {@code sampled} flag
 * — every request in this reactor is traced today, there's no volume-based sampling need at this
 * scale. The flag still round-trips correctly for a future service that does implement it.
 *
 * @param traceId  32 lowercase hex characters (16 bytes), constant for the whole distributed call
 * @param spanId   16 lowercase hex characters (8 bytes), unique per hop
 * @param sampled  whether this trace should be recorded; always {@code true} for a freshly
 *                 generated context in this reactor (see class Javadoc)
 */
public record TraceContext(String traceId, String spanId, boolean sampled) {

    /** The standard header name this context is read from and written to. */
    public static final String HEADER_NAME = "traceparent";

    private static final String VERSION = "00";
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Parses a {@code traceparent} header value.
     *
     * @param headerValue the raw header value, possibly {@code null} or malformed
     * @return the parsed context, or {@link Optional#empty()} if {@code headerValue} is
     *         {@code null}, blank, or does not match the expected {@code 00-...-...-...} shape
     */
    public static Optional<TraceContext> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        var matcher = TRACEPARENT_PATTERN.matcher(headerValue.trim().toLowerCase());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        boolean sampled = (Integer.parseInt(matcher.group(3), 16) & 0x01) == 0x01;
        return Optional.of(new TraceContext(matcher.group(1), matcher.group(2), sampled));
    }

    /**
     * Starts a brand-new trace — used when no {@code traceparent} header arrived at all (this
     * service is the first hop, e.g. a request that bypassed {@code gateway} and hit a backend
     * service directly, or a background job with no inbound request to inherit from).
     *
     * @return a new context with a freshly generated trace-id and span-id, sampled
     */
    public static TraceContext fresh() {
        return new TraceContext(randomHex(16), randomHex(8), true);
    }

    /**
     * Derives this service's own span from an inbound context — same {@link #traceId}, a freshly
     * generated {@link #spanId}. The span-id on {@code this} represents whoever called us; the
     * span-id on the result represents our own handling, and is what we hand to whichever service
     * we call next as its inbound {@code parent-id}.
     *
     * @return a new context sharing this trace but with a new span-id
     */
    public TraceContext withNewSpan() {
        return new TraceContext(traceId, randomHex(8), sampled);
    }

    /**
     * Formats this context back into a {@code traceparent} header value.
     *
     * @return {@code "00-" + traceId + "-" + spanId + "-" + ("01" or "00")}
     */
    public String toHeaderValue() {
        return VERSION + "-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
    }

    private static String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
