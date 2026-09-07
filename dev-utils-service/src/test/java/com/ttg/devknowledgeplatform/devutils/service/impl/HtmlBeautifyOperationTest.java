package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class HtmlBeautifyOperationTest {

    private final HtmlBeautifyOperation operation = new HtmlBeautifyOperation();

    @Test
    void indentsNestedElementsByDefault() {
        String result = operation.execute("<div><p>Hello</p><p>World</p></div>", false);

        assertThat(result).contains("\n").contains("  <p>");
    }

    @Test
    void producesUnformattedOutputWhenMinified() {
        String result = operation.execute("<div><p>Hello</p><p>World</p></div>", true);

        assertThat(result).doesNotContain("\n");
    }

    @Test
    void neverThrowsOnMalformedMarkup() {
        assertThatCode(() -> operation.execute("<div><p>unclosed<span>oops</div>", false))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesTextContent() {
        String result = operation.execute("<p>Hello, World!</p>", true);

        assertThat(result).contains("Hello, World!");
    }
}
