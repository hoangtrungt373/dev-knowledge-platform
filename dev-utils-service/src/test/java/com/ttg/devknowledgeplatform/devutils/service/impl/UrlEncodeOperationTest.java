package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlEncodeOperationTest {

    private final UrlEncodeOperation operation = new UrlEncodeOperation();

    @Test
    void encodesAUrlWithReservedDelimiters() {
        assertThat(operation.execute("https://translate.google.com/?hl=vi&sl=vi&tl=en&op=translate"))
                .isEqualTo("https%3A%2F%2Ftranslate.google.com%2F%3Fhl%3Dvi%26sl%3Dvi%26tl%3Den%26op%3Dtranslate");
    }

    // URLEncoder's own default represents a space as '+' (form-encoding) — this operation corrects
    // that to '%20', matching conventional URL percent-encoding (e.g. encodeURIComponent).
    @Test
    void encodesASpaceAsPercentTwentyNotPlus() {
        assertThat(operation.execute("Vui Coding")).isEqualTo("Vui%20Coding");
    }

    @Test
    void emptyStringEncodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
