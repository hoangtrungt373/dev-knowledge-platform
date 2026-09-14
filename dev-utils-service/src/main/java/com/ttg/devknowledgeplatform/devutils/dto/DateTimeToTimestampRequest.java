package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for {@code DateTimeToUnixOperation} — {@code dateTime} is a local date-time string in
 * {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE_TIME} shape (e.g.
 * {@code "2026-01-15T10:30:00"}), the exact value shape an HTML {@code <input type="datetime-
 * local">} already produces, so the GUI needs no reformatting before sending it. {@code zoneId}
 * is the IANA zone {@code dateTime} is interpreted in — deliberately no {@code @NotBlank}, unlike
 * {@code dateTime}: comparing/converting against the default UTC zone (see
 * {@code service.impl.support.TimeZones#parse}) is a legitimate choice for a caller with no zone
 * preference, the same "blank is a real case, not an error" reasoning {@code TextDiffRequest}'s
 * own fields already establish.
 *
 * @param dateTime a local date-time string, {@code ISO_LOCAL_DATE_TIME} shape.
 * @param zoneId   an IANA zone id (e.g. {@code "Asia/Ho_Chi_Minh"}), or blank for UTC.
 */
public record DateTimeToTimestampRequest(
        @NotBlank @Size(max = 64) String dateTime,
        @Size(max = 64) String zoneId) {
}
