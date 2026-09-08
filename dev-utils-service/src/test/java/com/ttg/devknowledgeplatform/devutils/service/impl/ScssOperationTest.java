package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ScssOperationTest {

    private final ScssOperation operation = new ScssOperation();

    @Test
    void indentsNestedRulesByDefault() {
        String result = operation.execute(".a { .b { color: red; } }", false);

        assertThat(result).contains("\n").contains("  .b {");
    }

    @Test
    void preservesScssVariablesAndNestingLiterally() {
        // This operation only reformats — it doesn't resolve/compile SCSS's own extensions.
        String result = operation.execute("$width: 10px;\n.a { width: $width; &:hover { color: red; } }", false);

        assertThat(result).contains("$width").contains("&:hover");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute(".a {\n  color: red;\n}", true);

        assertThat(result).isEqualTo(".a{color:red;}");
    }

    @Test
    void neverThrowsOnMalformedScss() {
        assertThatCode(() -> operation.execute(".a { .b { color: red", false)).doesNotThrowAnyException();
    }
}
