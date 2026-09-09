package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class Base64DecodeOperationTest {

    private final Base64DecodeOperation operation = new Base64DecodeOperation();

    @Test
    void decodesPlainAsciiText() {
        assertThat(operation.execute("VnVpIENvZGluZw==")).isEqualTo("Vui Coding");
    }

    // The other direction of Base64EncodeOperationTest's own matching case — same "verified
    // against a real standalone Java harness first" discipline.
    @Test
    void decodesMultiByteUtf8TextIncludingAnEmoji() {
        assertThat(operation.execute("SGVsbG8gZnJvbSBWdWkgQ29kaW5nIPCfkYs="))
                .isEqualTo("Hello from Vui Coding 👋");
    }

    @Test
    void toleratesALeadingOrTrailingNewlineFromAPastedString() {
        assertThat(operation.execute("\nVnVpIENvZGluZw==\n")).isEqualTo("Vui Coding");
    }

    @Test
    void emptyStringDecodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }

    @Test
    void malformedBase64ThrowsBusinessExceptionWithInvalidBase64ErrorCode() {
        assertThatThrownBy(() -> operation.execute("not valid base64!!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_BASE64);
    }
}
