package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class HexToAsciiOperationTest {

    private final HexToAsciiOperation operation = new HexToAsciiOperation();

    @Test
    void decodesTheExactReportedExample() {
        assertThat(operation.execute("53 65 72 69 61 6c 69 7a 65 20 4a 53 4f 4e")).isEqualTo("Serialize JSON");
    }

    // Tolerant of a continuous hex string with no separator at all — not just AsciiToHexOperation's
    // own space-separated output shape.
    @Test
    void decodesAContinuousHexStringWithNoSeparator() {
        assertThat(operation.execute("48656c6c6f")).isEqualTo("Hello");
    }

    // Also tolerant of other whitespace shapes (tabs/newlines), not just single spaces.
    @Test
    void decodesHexSeparatedByArbitraryWhitespace() {
        assertThat(operation.execute("48\t65\n6c 6c6f")).isEqualTo("Hello");
    }

    // The inverse of AsciiToHexOperationTest's own multi-byte case — verified against the same
    // real standalone Java harness first.
    @Test
    void decodesAMultiByteUtf8Sequence() {
        assertThat(operation.execute("63 61 66 c3 a9")).isEqualTo("café");
    }

    @Test
    void emptyStringDecodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }

    @Test
    void oddNumberOfHexDigitsThrowsBusinessExceptionWithInvalidHexErrorCode() {
        assertThatThrownBy(() -> operation.execute("abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_HEX);
    }

    @Test
    void nonHexCharacterThrowsBusinessExceptionWithInvalidHexErrorCode() {
        assertThatThrownBy(() -> operation.execute("zz"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_HEX);
    }
}
