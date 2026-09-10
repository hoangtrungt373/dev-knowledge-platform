package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class CronParserOperationTest {

    private final CronParserOperation operation = new CronParserOperation();

    @Test
    void describesTheExactReportedExample() {
        assertThat(operation.execute("0 9 * * 1-5")).isEqualTo("At 09:00, Monday through Friday");
    }

    @Test
    void everyFieldWildcardDescribesEveryMinute() {
        assertThat(operation.execute("* * * * *")).isEqualTo("Every minute");
    }

    @Test
    void aMinuteStepWithWildcardHourDescribesEveryNMinutes() {
        assertThat(operation.execute("*/5 * * * *")).isEqualTo("Every 5 minutes");
    }

    @Test
    void anHourStepAtMinuteZeroDescribesEveryNHours() {
        assertThat(operation.execute("0 */2 * * *")).isEqualTo("Every 2 hours");
    }

    @Test
    void aSingleDayOfMonthIsDescribed() {
        assertThat(operation.execute("0 9 1 * *")).isEqualTo("At 09:00, on day 1 of the month");
    }

    @Test
    void aSingleMonthIsDescribed() {
        assertThat(operation.execute("0 9 1 1 *")).isEqualTo("At 09:00, on day 1 of the month, only in January");
    }

    @Test
    void dayOfWeekNamesResolveTheSameAsNumbers() {
        assertThat(operation.execute("0 9 * * MON-FRI")).isEqualTo("At 09:00, Monday through Friday");
        assertThat(operation.execute("0 9 * * mon-fri")).isEqualTo("At 09:00, Monday through Friday");
    }

    @Test
    void anArithmeticProgressionOfDaysIsDescribedAsAStep() {
        // 1,3,5 is a genuine arithmetic progression (constant step of 2), not an arbitrary list —
        // the classifier correctly prefers the more informative "every 2 days" phrasing over a
        // flat list here.
        assertThat(operation.execute("0 9 * * 1,3,5")).isEqualTo("At 09:00, every 2 days, starting Monday");
    }

    @Test
    void aNonArithmeticDayOfWeekListIsJoinedWithAnOxfordComma() {
        // 1,2,5 has no constant step (1, then 3) — genuinely falls through to the arbitrary-list
        // phrasing.
        assertThat(operation.execute("0 9 * * 1,2,5")).isEqualTo("At 09:00, on Monday, Tuesday, and Friday");
    }

    @Test
    void sundayAsSevenNormalizesTheSameAsSundayAsZero() {
        assertThat(operation.execute("0 0 * * 7")).isEqualTo("At 00:00, only on Sunday");
        assertThat(operation.execute("0 0 * * 0")).isEqualTo("At 00:00, only on Sunday");
    }

    @Test
    void dayOfMonthAndDayOfWeekBothRestrictedAreJoinedWithOr() {
        // Real POSIX semantics: when both are restricted, the schedule fires when EITHER
        // matches, not both at once — "or", not the implicit "and" every other clause carries.
        assertThat(operation.execute("0 0 1 * 1")).isEqualTo("At 00:00, on day 1 of the month, or only on Monday");
    }

    @Test
    void aWrapAroundDayOfWeekRangeResolvesCorrectly() {
        // FRI-MON wraps: Friday, Saturday, Sunday, Monday — not contiguous in the 0(Sun)-6(Sat)
        // canonical numbering, so this falls through to the arbitrary-list phrasing.
        assertThat(operation.execute("0 0 * * FRI-MON")).isEqualTo("At 00:00, on Sunday, Monday, Friday, and Saturday");
    }

    @Test
    void aNonDefaultCaseAndExtraWhitespaceIsTolerated() {
        assertThat(operation.execute("  0   9  *  *  1-5  ")).isEqualTo("At 09:00, Monday through Friday");
    }

    @Test
    void rejectsTheWrongNumberOfFields() {
        assertThatThrownBy(() -> operation.execute("0 9 * *"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_CRON))
                .hasMessageContaining("found 4");
    }

    @Test
    void rejectsANonNumericUnrecognizedToken() {
        assertThatThrownBy(() -> operation.execute("0 9 * * XYZ"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_CRON));
    }

    @Test
    void rejectsAnOutOfRangeValue() {
        assertThatThrownBy(() -> operation.execute("99 9 * * *"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_CRON));
    }

    @Test
    void rejectsANonPositiveStep() {
        assertThatThrownBy(() -> operation.execute("*/0 * * * *"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_CRON));
    }
}
