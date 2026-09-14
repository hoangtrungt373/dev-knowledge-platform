package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class RegexTesterOperationTest {

    private final RegexTesterOperation operation = new RegexTesterOperation();

    // testText mirrors the GUI's own shared SAMPLE_EMAIL_TEST_TEXT constant
    // (utils/regexInputFormat.ts) — two IANA-reserved example domains (RFC 2606), not a made-up
    // one, since this is exactly the sample every real regex tester's own docs use for this kind of
    // demo.
    @Test
    void matchesTheExactReportedExample() {
        String result = operation.execute(
                "[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}",
                "gi",
                "hello@example.com\nsupport@example.org\nnot-an-email");

        assertThat(result).isEqualTo("hello@example.com\n\nsupport@example.org");
    }

    @Test
    void returnsOnlyTheFirstMatchWithoutTheGlobalFlag() {
        String result = operation.execute(
                "[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}",
                "i",
                "hello@example.com\nsupport@example.org\nnot-an-email");

        assertThat(result).isEqualTo("hello@example.com");
    }

    @Test
    void caseInsensitiveFlagMatchesBothCasings() {
        String result = operation.execute("hello", "gi", "Hello HELLO hello");

        assertThat(result).isEqualTo("Hello\n\nHELLO\n\nhello");
    }

    @Test
    void withoutCaseInsensitiveFlagOnlyExactCasingMatches() {
        String result = operation.execute("hello", "g", "Hello HELLO hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void multilineFlagLetsAnchorsMatchAtEachLineBoundary() {
        String result = operation.execute("^\\d+$", "gm", "123\nabc\n456");

        assertThat(result).isEqualTo("123\n\n456");
    }

    @Test
    void returnsNoMatchesFoundMessageWhenNothingMatches() {
        String result = operation.execute("xyz", "g", "abc def");

        assertThat(result).isEqualTo("No matches found.");
    }

    @Test
    void anUnrecognizedFlagCharacterIsIgnoredRatherThanRejected() {
        String result = operation.execute("hello", "gy", "hello world");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void rejectsAPatternThatFailsToCompile() {
        assertThatThrownBy(() -> operation.execute("[unclosed", "g", "anything"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_REGEX));
    }

    // Confirmed via a real standalone Java harness first that this exact pattern/length
    // combination reliably exceeds RegexTesterOperation's own 2-second timeout on this JDK (the
    // classic single-nested-quantifier "evil regex" examples no longer reproduce reliably — this
    // shape, several greedy `.*` groups against non-matching input, still does).
    @Test
    void timesOutOnACatastrophicallyBacktrackingPattern() {
        String evilInput = "a".repeat(200);

        assertThatThrownBy(() -> operation.execute("^(.*)(.*)(.*)(.*)=x$", "", evilInput))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.REGEX_TIMEOUT));
    }
}
