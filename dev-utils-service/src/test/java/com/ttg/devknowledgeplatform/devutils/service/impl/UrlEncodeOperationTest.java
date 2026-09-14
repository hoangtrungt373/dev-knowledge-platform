package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlEncodeOperationTest {

    private final UrlEncodeOperation operation = new UrlEncodeOperation();

    @Test
    void encodesAUrlWithReservedDelimiters() {
        assertThat(operation.execute("https://devknowledge.io/search?q=dev tools&sort=stars desc"))
                .isEqualTo("https%3A%2F%2Fdevknowledge.io%2Fsearch%3Fq%3Ddev%20tools%26sort%3Dstars%20desc");
    }

    // URLEncoder's own default represents a space as '+' (form-encoding) — this operation corrects
    // that to '%20', matching conventional URL percent-encoding (e.g. encodeURIComponent).
    @Test
    void encodesASpaceAsPercentTwentyNotPlus() {
        assertThat(operation.execute("Dev Knowledge")).isEqualTo("Dev%20Knowledge");
    }

    @Test
    void emptyStringEncodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
