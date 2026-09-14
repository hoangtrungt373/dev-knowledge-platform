package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response for {@code DateTimeToUnixOperation} — the same instant expressed as both Unix units
 * at once, so a caller never has to guess which one they actually wanted.
 *
 * @param epochSeconds seconds since the Unix epoch (1970-01-01T00:00:00Z).
 * @param epochMillis  milliseconds since the Unix epoch.
 */
public record TimestampResponse(long epochSeconds, long epochMillis) {
}
