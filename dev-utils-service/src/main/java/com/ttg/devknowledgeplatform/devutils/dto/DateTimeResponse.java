package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response for {@code UnixToDateTimeOperation} — the fourth operation in this module (after
 * {@code StringCaseResponse}/{@code HashResponse}/{@code TextDiffResponse}) whose output is
 * genuinely richer than a single string: one resolved instant rendered 7 independently useful
 * ways at once, plus the resolved epoch value in both units (so a caller who typed seconds can
 * see the equivalent milliseconds, or vice versa, without a second round trip) — a timestamp
 * converter's whole value proposition is showing several representations side by side rather
 * than making the caller pick one and round-trip for the rest.
 *
 * @param local     the instant formatted as {@code "yyyy-MM-dd HH:mm:ss"} in the request's own
 *                  {@code zoneId} (or UTC, if left blank).
 * @param utc       the same format, always in UTC, with a trailing {@code " UTC"} suffix for
 *                  clarity.
 * @param iso8601   the standard {@link java.time.Instant#toString()} form (e.g.
 *                  {@code "2026-01-15T10:30:00Z"}).
 * @param rfc1123   the HTTP-date form ({@link java.time.format.DateTimeFormatter#RFC_1123_DATE_TIME}),
 *                  the exact shape an HTTP {@code Date}/{@code Last-Modified} header uses.
 * @param sql       {@code "yyyy-MM-dd HH:mm:ss"} in UTC with no suffix — the plain, naive form
 *                  most SQL {@code TIMESTAMP} columns expect.
 * @param relative  a human sentence relative to now, e.g. {@code "3 hours ago"}, {@code "in 2
 *                  days"}, or {@code "just now"}.
 * @param dayOfWeek the full English day-of-week name, in the request's own {@code zoneId}.
 * @param epochSeconds the resolved instant, in seconds.
 * @param epochMillis  the resolved instant, in milliseconds.
 */
public record DateTimeResponse(
        String local,
        String utc,
        String iso8601,
        String rfc1123,
        String sql,
        String relative,
        String dayOfWeek,
        long epochSeconds,
        long epochMillis) {
}
