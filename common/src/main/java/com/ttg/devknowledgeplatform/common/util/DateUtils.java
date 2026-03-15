package com.ttg.devknowledgeplatform.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 *
 * @author ttg
 */
public class DateUtils {

    private DateUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Get current date and time as Instant (UTC)
     *
     * @return current Instant
     */
    public static Instant getCurrentDateTime() {
        return Instant.now();
    }

    /**
     * Get current date and time as ZonedDateTime in system default timezone
     *
     * @return current ZonedDateTime
     */
    public static ZonedDateTime getCurrentZonedDateTime() {
        return ZonedDateTime.now();
    }

    /**
     * Get current date and time as ZonedDateTime in specified timezone
     *
     * @param zoneId timezone ID
     * @return current ZonedDateTime in specified timezone
     */
    public static ZonedDateTime getCurrentZonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.now(zoneId);
    }

    /**
     * Get current date and time as ZonedDateTime in UTC
     *
     * @return current ZonedDateTime in UTC
     */
    public static ZonedDateTime getCurrentZonedDateTimeUTC() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }
}
