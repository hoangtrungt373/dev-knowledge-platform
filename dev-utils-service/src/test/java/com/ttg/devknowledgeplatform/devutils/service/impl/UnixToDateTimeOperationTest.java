package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.DateTimeResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class UnixToDateTimeOperationTest {

    private final UnixToDateTimeOperation operation = new UnixToDateTimeOperation();

    // Every literal value below (local/utc/iso8601/rfc1123/sql/dayOfWeek for the epoch itself)
    // was confirmed against a real JDK 21 harness first, not assumed — including the genuinely
    // surprising RFC_1123_DATE_TIME output ("1 Jan", not the zero-padded "01 Jan" one might
    // expect).
    @Test
    void decodesTheUnixEpochItselfInUtc() {
        DateTimeResponse result = operation.execute("0", "");
        assertThat(result.local()).isEqualTo("1970-01-01 00:00:00");
        assertThat(result.utc()).isEqualTo("1970-01-01 00:00:00 UTC");
        assertThat(result.iso8601()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(result.rfc1123()).isEqualTo("Thu, 1 Jan 1970 00:00:00 GMT");
        assertThat(result.sql()).isEqualTo("1970-01-01 00:00:00");
        assertThat(result.dayOfWeek()).isEqualTo("Thursday");
        assertThat(result.epochSeconds()).isZero();
        assertThat(result.epochMillis()).isZero();
        // Epoch 0 is always in the past relative to "now" (whenever this test actually runs), so
        // this is deterministic without pinning an exact phrase to a specific test-run date.
        assertThat(result.relative()).endsWith("ago");
    }

    // Confirms the local/dayOfWeek fields actually honor zoneId, not just echo UTC back — LA is
    // UTC-8 (standard time, no DST) in January, which rolls the epoch back to the previous
    // calendar day.
    @Test
    void localAndDayOfWeekReflectTheRequestedZone() {
        DateTimeResponse result = operation.execute("0", "America/Los_Angeles");
        assertThat(result.local()).isEqualTo("1969-12-31 16:00:00");
        assertThat(result.dayOfWeek()).isEqualTo("Wednesday");
        // utc/iso8601/epoch fields never depend on zoneId — only the local/dayOfWeek fields do.
        assertThat(result.utc()).isEqualTo("1970-01-01 00:00:00 UTC");
        assertThat(result.epochSeconds()).isZero();
    }

    @Test
    void nullZoneIdDefaultsToUtcTheSameAsBlank() {
        DateTimeResponse result = operation.execute("0", null);
        assertThat(result.local()).isEqualTo("1970-01-01 00:00:00");
    }

    // A 10-digit (or fewer) magnitude is auto-detected as seconds...
    @Test
    void aTenDigitValueIsAutoDetectedAsSeconds() {
        DateTimeResponse result = operation.execute("9999999999", "");
        assertThat(result.epochSeconds()).isEqualTo(9_999_999_999L);
        assertThat(result.epochMillis()).isEqualTo(9_999_999_999_000L);
    }

    // ...while an 11+ digit magnitude (at or over the 10,000,000,000 threshold) is auto-detected
    // as milliseconds instead — the exact boundary this class's own SECONDS_VS_MILLIS_THRESHOLD
    // documents, confirmed via a real harness (Math.abs(value) < threshold) before writing this.
    @Test
    void anElevenDigitValueIsAutoDetectedAsMilliseconds() {
        DateTimeResponse result = operation.execute("10000000000", "");
        assertThat(result.epochMillis()).isEqualTo(10_000_000_000L);
        assertThat(result.epochSeconds()).isEqualTo(10_000_000L);
    }

    // Seconds and the equivalent milliseconds for the same real-world instant resolve to an
    // identical response — the whole point of auto-detecting the unit rather than requiring the
    // caller to specify it.
    @Test
    void equivalentSecondsAndMillisecondsInputsProduceTheIdenticalResponse() {
        DateTimeResponse fromSeconds = operation.execute("1704067200", "UTC");
        DateTimeResponse fromMillis = operation.execute("1704067200000", "UTC");
        assertThat(fromSeconds).isEqualTo(fromMillis);
    }

    @Test
    void malformedTimestampThrowsBusinessExceptionWithInvalidTimestampErrorCode() {
        assertThatThrownBy(() -> operation.execute("not-a-number", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_TIMESTAMP);
    }

    @Test
    void unrecognizedZoneIdThrowsBusinessExceptionWithInvalidTimeZoneErrorCode() {
        assertThatThrownBy(() -> operation.execute("0", "Not/AZone"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_TIME_ZONE);
    }

    // formatRelative is package-private specifically for this direct, clock-independent testing —
    // see its own Javadoc. Every bucket boundary exercised against fixed instants, never
    // Instant.now(), so none of this can ever be flaky.
    @Test
    void formatRelativeJustNowUnderFiveSeconds() {
        Instant reference = Instant.parse("2024-01-01T00:00:00Z");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(3), reference)).isEqualTo("just now");
        assertThat(UnixToDateTimeOperation.formatRelative(reference, reference)).isEqualTo("just now");
    }

    @Test
    void formatRelativeSeconds() {
        Instant reference = Instant.parse("2024-01-01T00:00:00Z");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(30), reference)).isEqualTo("30 seconds ago");
    }

    @Test
    void formatRelativeSingularVsPlural() {
        Instant reference = Instant.parse("2024-01-01T00:00:00Z");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(90), reference)).isEqualTo("1 minute ago");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(150), reference)).isEqualTo("2 minutes ago");
    }

    @Test
    void formatRelativeHoursDaysMonthsYears() {
        Instant reference = Instant.parse("2024-01-01T00:00:00Z");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(7_200), reference)).isEqualTo("2 hours ago");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(172_800), reference)).isEqualTo("2 days ago");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(5_184_000), reference)).isEqualTo("2 months ago");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.minusSeconds(63_072_000), reference)).isEqualTo("2 years ago");
    }

    @Test
    void formatRelativeFutureInstantsReadAsInsteadOfAgo() {
        Instant reference = Instant.parse("2024-01-01T00:00:00Z");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.plusSeconds(3_600), reference)).isEqualTo("in 1 hour");
        assertThat(UnixToDateTimeOperation.formatRelative(reference.plusSeconds(172_800), reference)).isEqualTo("in 2 days");
    }
}
