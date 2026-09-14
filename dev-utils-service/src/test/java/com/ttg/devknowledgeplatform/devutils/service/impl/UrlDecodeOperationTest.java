package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class UrlDecodeOperationTest {

    private final UrlDecodeOperation operation = new UrlDecodeOperation();

    @Test
    void decodesAUrlWithReservedDelimiters() {
        assertThat(operation.execute("https%3A%2F%2Fdevknowledge.io%2Fsearch%3Fq%3Ddev%20tools%26sort%3Dstars%20desc"))
                .isEqualTo("https://devknowledge.io/search?q=dev tools&sort=stars desc");
    }

    // Matches UrlEncodeOperation's own '%20' output, and also tolerates a literal '+' the way
    // conventional query-string decoding does (URLDecoder's own behavior).
    @Test
    void decodesPercentTwentyAndPlusBackIntoASpace() {
        assertThat(operation.execute("Dev%20Knowledge")).isEqualTo("Dev Knowledge");
        assertThat(operation.execute("Dev+Knowledge")).isEqualTo("Dev Knowledge");
    }

    @Test
    void emptyStringDecodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }

    @Test
    void malformedPercentEncodingThrowsBusinessExceptionWithInvalidUrlEncodingErrorCode() {
        assertThatThrownBy(() -> operation.execute("100%"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_URL_ENCODING);
    }
}
