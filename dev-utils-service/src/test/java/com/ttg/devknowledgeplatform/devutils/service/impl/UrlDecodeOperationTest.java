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
        assertThat(operation.execute("https%3A%2F%2Ftranslate.google.com%2F%3Fhl%3Dvi%26sl%3Dvi%26tl%3Den%26op%3Dtranslate"))
                .isEqualTo("https://translate.google.com/?hl=vi&sl=vi&tl=en&op=translate");
    }

    // Matches UrlEncodeOperation's own '%20' output, and also tolerates a literal '+' the way
    // conventional query-string decoding does (URLDecoder's own behavior).
    @Test
    void decodesPercentTwentyAndPlusBackIntoASpace() {
        assertThat(operation.execute("Vui%20Coding")).isEqualTo("Vui Coding");
        assertThat(operation.execute("Vui+Coding")).isEqualTo("Vui Coding");
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
