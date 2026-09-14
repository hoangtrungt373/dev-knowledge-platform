package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.DateTimeResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.TimeZones;

/**
 * Unix Time Converter's "Unix → Date/Time" direction — see {@link DateTimeToUnixOperation}'s own
 * Javadoc for the shared {@link OperationGroup#FORMATTERS} reasoning. Renders one resolved
 * instant 7 different ways at once (via {@link DateTimeResponse}, plus the resolved epoch value
 * in both units) — a real, deliberate departure from every other operation's single-
 * representation output, since a timestamp converter's whole value proposition is showing
 * several representations side by side rather than making the caller pick one and round-trip
 * for the rest.
 *
 * <p>The input timestamp's own unit (seconds vs. milliseconds) is auto-detected by magnitude,
 * not a caller-supplied flag — the standard heuristic every comparable tool (epochconverter.com,
 * etc.) uses: a value whose absolute magnitude is under {@link #SECONDS_VS_MILLIS_THRESHOLD}
 * (10 digits — everything up to roughly the year 2286 in seconds) is treated as seconds; anything
 * at or above that is treated as milliseconds. This is deliberately the simplest possible rule
 * (a caller who genuinely means a tiny millisecond value under that threshold — a moment within
 * ~4 months of the epoch itself — is a vanishingly rare case not worth a whole extra request
 * field for) rather than an explicit unit selector.
 */
@Component
public class UnixToDateTimeOperation implements DevUtilOperation {

    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final long SECONDS_VS_MILLIS_THRESHOLD = 10_000_000_000L;

    @Override
    public OperationGroup group() {
        return OperationGroup.FORMATTERS;
    }

    /**
     * @param timestamp a Unix timestamp in seconds or milliseconds (auto-detected — see this
     *                  class's own Javadoc).
     * @param zoneId    an IANA zone id the response's own {@code local} field is rendered in, or
     *                  blank for UTC.
     * @return the resolved instant, rendered 7 different ways.
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_TIMESTAMP} when
     *                           {@code timestamp} isn't a valid number or is out of the range an
     *                           {@link Instant} can represent, or
     *                           {@link DevUtilsErrorCode#INVALID_TIME_ZONE} when {@code zoneId}
     *                           is non-blank but not a real zone id.
     */
    public DateTimeResponse execute(String timestamp, String zoneId) {
        Instant instant = parseInstant(timestamp);
        ZoneId zone = TimeZones.parse(zoneId);
        ZonedDateTime zoned = instant.atZone(zone);
        String local = zoned.format(LOCAL_FORMAT);
        String utc = instant.atZone(ZoneOffset.UTC).format(LOCAL_FORMAT) + " UTC";
        String iso8601 = instant.toString();
        String rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC));
        String sql = instant.atZone(ZoneOffset.UTC).format(LOCAL_FORMAT);
        String relative = formatRelative(instant, Instant.now());
        String dayOfWeek = zoned.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return new DateTimeResponse(
                local, utc, iso8601, rfc1123, sql, relative, dayOfWeek, instant.getEpochSecond(), instant.toEpochMilli());
    }

    private static Instant parseInstant(String raw) {
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_TIMESTAMP, (Object) raw);
        }
        try {
            long millis = Math.abs(value) < SECONDS_VS_MILLIS_THRESHOLD ? Math.multiplyExact(value, 1_000L) : value;
            return Instant.ofEpochMilli(millis);
        } catch (ArithmeticException | DateTimeException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_TIMESTAMP, (Object) raw);
        }
    }

    /**
     * Package-private (not private) specifically so {@code UnixToDateTimeOperationTest} can
     * exercise the relative-time phrasing directly against a fixed reference instant — the same
     * "package-private for test visibility, not extracted to its own file" precedent
     * {@code TextDiffOperation#MAX_LINES} already establishes. Not pulled out into its own
     * {@code service.impl.support} class the way {@link TimeZones} was — this logic has exactly
     * one call site today, unlike {@code TimeZones}, which both operations in this pair already
     * call (the "real 2nd usage" bar this module's support-class extractions require).
     *
     * @param target    the instant being described.
     * @param reference "now" — a real parameter, not a hidden {@link Instant#now()} call inside
     *                  this method, specifically so a test can pass a fixed value instead of
     *                  racing the clock.
     * @return e.g. {@code "3 hours ago"}, {@code "in 2 days"}, or {@code "just now"}.
     */
    static String formatRelative(Instant target, Instant reference) {
        Duration duration = Duration.between(target, reference);
        boolean past = !duration.isNegative();
        long seconds = duration.abs().getSeconds();
        if (seconds < 5) {
            return "just now";
        }
        long amount;
        String unit;
        if (seconds < 60) {
            amount = seconds;
            unit = "second";
        } else if (seconds < 3_600) {
            amount = seconds / 60;
            unit = "minute";
        } else if (seconds < 86_400) {
            amount = seconds / 3_600;
            unit = "hour";
        } else if (seconds < 2_592_000) {
            amount = seconds / 86_400;
            unit = "day";
        } else if (seconds < 31_536_000) {
            amount = seconds / 2_592_000;
            unit = "month";
        } else {
            amount = seconds / 31_536_000;
            unit = "year";
        }
        String phrase = amount + " " + unit + (amount == 1 ? "" : "s");
        return past ? phrase + " ago" : "in " + phrase;
    }
}
