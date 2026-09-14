package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for {@code UnixToDateTimeOperation} — {@code timestamp} is a Unix timestamp, either
 * seconds or milliseconds (auto-detected by magnitude — see that operation's own Javadoc for the
 * exact heuristic); {@code zoneId} is the IANA zone the response's own {@code local} field is
 * rendered in, deliberately no {@code @NotBlank} for the same "blank is a real case, not an
 * error" reasoning {@link DateTimeToTimestampRequest#zoneId()} already establishes.
 *
 * @param timestamp a Unix timestamp in seconds or milliseconds.
 * @param zoneId    an IANA zone id (e.g. {@code "Asia/Ho_Chi_Minh"}), or blank for UTC.
 */
public record TimestampToDateTimeRequest(
        @NotBlank @Size(max = 32) String timestamp,
        @Size(max = 64) String zoneId) {
}
