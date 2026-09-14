package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.TimestampResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class DateTimeToUnixOperationTest {

    private final DateTimeToUnixOperation operation = new DateTimeToUnixOperation();

    @Test
    void epochItselfConvertsToZero() {
        TimestampResponse result = operation.execute("1970-01-01T00:00:00", "");
        assertThat(result.epochSeconds()).isZero();
        assertThat(result.epochMillis()).isZero();
    }

    // Blank/null zoneId both default to UTC — a legitimate "no preference" case, not an error
    // (see DateTimeToTimestampRequest's own Javadoc for why zoneId carries no @NotBlank).
    @Test
    void nullZoneIdDefaultsToUtcTheSameAsBlank() {
        TimestampResponse result = operation.execute("1970-01-01T00:00:00", null);
        assertThat(result.epochSeconds()).isZero();
    }

    @Test
    void aKnownDateConvertsToItsRealWorldEpochSeconds() {
        // 2024-01-01T00:00:00Z is the well-known epoch value 1704067200 — confirmed via a real
        // JDK 21 harness (Instant.parse("2024-01-01T00:00:00Z").getEpochSecond()) before writing
        // this assertion, not assumed.
        TimestampResponse result = operation.execute("2024-01-01T00:00:00", "UTC");
        assertThat(result.epochSeconds()).isEqualTo(1_704_067_200L);
        assertThat(result.epochMillis()).isEqualTo(1_704_067_200_000L);
    }

    // Asia/Ho_Chi_Minh is UTC+8 for this specific 1970 date, not the +7 it uses today — confirmed
    // via a real JDK 21 harness first (not assumed): IANA tzdata records Vietnam's move from +8
    // to +7 in June 1975, so a date/time this early still resolves against the historical +8
    // offset. 08:00 local on the epoch date, in this zone, is exactly UTC midnight.
    @Test
    void aNonUtcZoneOffsetsTheResolvedInstantCorrectly() {
        TimestampResponse result = operation.execute("1970-01-01T08:00:00", "Asia/Ho_Chi_Minh");
        assertThat(result.epochSeconds()).isZero();
    }

    @Test
    void malformedDateTimeThrowsBusinessExceptionWithInvalidDateTimeErrorCode() {
        assertThatThrownBy(() -> operation.execute("not-a-date", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_DATE_TIME);
    }

    @Test
    void unrecognizedZoneIdThrowsBusinessExceptionWithInvalidTimeZoneErrorCode() {
        assertThatThrownBy(() -> operation.execute("1970-01-01T00:00:00", "Not/AZone"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_TIME_ZONE);
    }
}
