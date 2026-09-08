package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class JsOperationTest {

    private final JsOperation operation = new JsOperation();

    @Test
    void indentsNestedBlocksByDefault() {
        String result = operation.execute("function f(){if(true){return 1;}}", false);

        assertThat(result).contains("\n").contains("    return 1;");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute("function f() {\n  return 1;\n}", true);

        assertThat(result).isEqualTo("function f(){return 1;}");
    }

    @Test
    void neverCollapsesALineBreakBetweenTwoOrdinaryTokens() {
        // The ASI-safety guarantee (see CurlyBraceFormatter's own Javadoc): `return\nx` must stay
        // exactly that — collapsing the line break would turn `return; x;` into `return x;`,
        // silently changing what the code actually returns.
        String beautified = operation.execute("function f() {\n  return\n  x;\n}", false);
        String minified = operation.execute("function f() {\n  return\n  x;\n}", true);

        assertThat(beautified).contains("return\n");
        assertThat(minified).contains("return\n");
    }

    @Test
    void doesNotReformatContentInsideStringOrTemplateLiterals() {
        String result = operation.execute("const s = `a{b;c}d`;", false);

        assertThat(result).contains("`a{b;c}d`");
    }

    @Test
    void neverThrowsOnMalformedJs() {
        assertThatCode(() -> operation.execute("function f() { return 1", false)).doesNotThrowAnyException();
    }
}
