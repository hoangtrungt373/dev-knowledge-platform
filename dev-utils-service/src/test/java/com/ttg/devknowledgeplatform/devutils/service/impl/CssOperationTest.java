package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CssOperationTest {

    private final CssOperation operation = new CssOperation();

    @Test
    void indentsNestedRulesByDefault() {
        String result = operation.execute(".a { color: red; }", false);

        assertThat(result).contains("\n").contains("  color: red;");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute(".a {\n  color: red;\n}", true);

        assertThat(result).isEqualTo(".a{color:red;}");
    }

    @Test
    void neverThrowsOnMalformedCss() {
        assertThatCode(() -> operation.execute(".a { color: red", false)).doesNotThrowAnyException();
    }
}
