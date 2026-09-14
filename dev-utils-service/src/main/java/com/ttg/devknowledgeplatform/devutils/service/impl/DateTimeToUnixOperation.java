package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.TimestampResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.TimeZones;

/**
 * Unix Time Converter's "Date/Time → Unix" direction — converts a local date-time string,
 * interpreted in a given IANA zone, into its Unix timestamp (both seconds and milliseconds at
 * once, via {@link TimestampResponse}). Declares {@link OperationGroup#FORMATTERS} — see that
 * enum's own updated Javadoc for why this bidirectional pair follows
 * {@code PhpToJsonOperation}/{@code JsonToPhpOperation}'s descriptively-named-pair shape rather
 * than {@link OperationGroup#ENCODERS_DECODERS}'s encode/decode vocabulary.
 *
 * <p>Accepts {@code dateTime} in {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME} shape — exactly
 * the value an HTML {@code <input type="datetime-local">} already produces, so the GUI needs no
 * reformatting before sending it. {@code zoneId} defaults to UTC when blank (see
 * {@link TimeZones#parse}) — this module has no persisted user/timezone context of any kind (a
 * fully public, stateless endpoint), so the caller (the GUI, which knows the browser's own
 * detected zone) is expected to supply it explicitly rather than the backend guessing.
 */
@Component
public class DateTimeToUnixOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.FORMATTERS;
    }

    /**
     * @param dateTime a local date-time string, {@code ISO_LOCAL_DATE_TIME} shape (e.g.
     *                 {@code "2026-01-15T10:30:00"}).
     * @param zoneId   an IANA zone id {@code dateTime} is interpreted in, or blank for UTC.
     * @return the resolved instant as both Unix seconds and milliseconds.
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_DATE_TIME} when
     *                           {@code dateTime} doesn't parse, or
     *                           {@link DevUtilsErrorCode#INVALID_TIME_ZONE} when {@code zoneId}
     *                           is non-blank but not a real zone id.
     */
    public TimestampResponse execute(String dateTime, String zoneId) {
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(dateTime.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_DATE_TIME, (Object) dateTime);
        }
        ZoneId zone = TimeZones.parse(zoneId);
        Instant instant = local.atZone(zone).toInstant();
        return new TimestampResponse(instant.getEpochSecond(), instant.toEpochMilli());
    }
}
