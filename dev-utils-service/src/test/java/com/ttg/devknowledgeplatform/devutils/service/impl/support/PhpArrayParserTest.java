package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PhpArrayParserTest {

    @Test
    @SuppressWarnings("unchecked")
    void parsesAnAssociativeArrayIntoALinkedHashMapPreservingOrder() {
        Object result = PhpArrayParser.parse("['name' => 'Vui Coding', 'active' => true, 'tools' => ['JSON', 'JWT']]");

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.keySet()).containsExactly("name", "active", "tools");
        assertThat(map.get("name")).isEqualTo("Vui Coding");
        assertThat(map.get("active")).isEqualTo(Boolean.TRUE);
        assertThat(map.get("tools")).isEqualTo(List.of("JSON", "JWT"));
    }

    @Test
    void parsesAPlainListWithNoKeysIntoAList() {
        Object result = PhpArrayParser.parse("['JSON', 'JWT']");

        assertThat(result).isEqualTo(List.of("JSON", "JWT"));
    }

    @Test
    void treatsExplicitSequentialZeroBasedKeysAsAList() {
        Object result = PhpArrayParser.parse("[0 => 'a', 1 => 'b', 2 => 'c']");

        assertThat(result).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void nonSequentialExplicitKeysBecomeAMapStringified() {
        Object result = PhpArrayParser.parse("[5 => 'a', 'x' => 'b']");

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.get("5")).isEqualTo("a");
        assertThat(map.get("x")).isEqualTo("b");
    }

    @Test
    void supportsLegacyArrayFunctionSyntax() {
        Object result = PhpArrayParser.parse("array('a', 'b')");

        assertThat(result).isEqualTo(List.of("a", "b"));
    }

    @Test
    void parsesNumbersBooleansAndNull() {
        // List.of(...) can't hold a null element, so this is checked index by index rather than
        // via one isEqualTo(List.of(...)) call.
        List<?> result = (List<?>) PhpArrayParser.parse("[1, -2, 1.5, true, false, null]");

        assertThat(result).hasSize(6);
        assertThat(result.get(0)).isEqualTo(1L);
        assertThat(result.get(1)).isEqualTo(-2L);
        assertThat(result.get(2)).isEqualTo(1.5);
        assertThat(result.get(3)).isEqualTo(true);
        assertThat(result.get(4)).isEqualTo(false);
        assertThat(result.get(5)).isNull();
    }

    @Test
    void singleQuotedStringsOnlyHonorEscapedQuoteAndBackslash() {
        Object result = PhpArrayParser.parse("['it\\'s', 'a\\\\b', 'a\\nb']");

        List<?> list = (List<?>) result;
        assertThat(list.get(0)).isEqualTo("it's");
        assertThat(list.get(1)).isEqualTo("a\\b");
        // \n is NOT a real escape in a single-quoted PHP string — stays as a literal backslash+n.
        assertThat(list.get(2)).isEqualTo("a\\nb");
    }

    @Test
    void doubleQuotedStringsHonorCommonEscapeSequences() {
        Object result = PhpArrayParser.parse("[\"a\\nb\", \"a\\tb\"]");

        List<?> list = (List<?>) result;
        assertThat(list.get(0)).isEqualTo("a\nb");
        assertThat(list.get(1)).isEqualTo("a\tb");
    }

    @Test
    void toleratesAFullPhpSnippetWithOpenTagReturnAndSemicolon() {
        Object result = PhpArrayParser.parse("<?php\nreturn ['a' => 1];\n");

        assertThat(result).isEqualTo(Map.of("a", 1L));
    }

    @Test
    void skipsLineAndBlockComments() {
        Object result = PhpArrayParser.parse("[\n  // a comment\n  'a', # another\n  /* block */ 'b'\n]");

        assertThat(result).isEqualTo(List.of("a", "b"));
    }

    @Test
    void trailingCommaIsAllowed() {
        Object result = PhpArrayParser.parse("['a', 'b',]");

        assertThat(result).isEqualTo(List.of("a", "b"));
    }

    @Test
    void throwsPhpParseExceptionWithLocationOnUnterminatedArray() {
        assertThatThrownBy(() -> PhpArrayParser.parse("['a', 'b'"))
                .isInstanceOf(PhpArrayParser.PhpParseException.class)
                .hasMessageContaining("line")
                .hasMessageContaining("column");
    }

    @Test
    void throwsPhpParseExceptionOnUnexpectedTrailingContent() {
        assertThatThrownBy(() -> PhpArrayParser.parse("['a'] garbage"))
                .isInstanceOf(PhpArrayParser.PhpParseException.class);
    }

    @Test
    void throwsPhpParseExceptionOnUnterminatedString() {
        assertThatThrownBy(() -> PhpArrayParser.parse("['a"))
                .isInstanceOf(PhpArrayParser.PhpParseException.class);
    }
}
