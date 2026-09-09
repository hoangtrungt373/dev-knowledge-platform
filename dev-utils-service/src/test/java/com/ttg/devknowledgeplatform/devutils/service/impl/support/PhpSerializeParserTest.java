package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PhpSerializeParserTest {

    @Test
    void parsesTheExactReportedExampleIntoAnOrderedMap() {
        Object result = PhpSerializeParser.parse(
                "a:3:{s:4:\"name\";s:12:\"DevKnowledge\";s:6:\"active\";b:1;s:5:\"count\";i:47;}");

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.keySet()).containsExactly("name", "active", "count");
        assertThat(map.get("name")).isEqualTo("DevKnowledge");
        assertThat(map.get("active")).isEqualTo(Boolean.TRUE);
        assertThat(map.get("count")).isEqualTo(47L);
    }

    @Test
    void parsesASequentialIntegerKeyedArrayAsAJavaList() {
        Object result = PhpSerializeParser.parse("a:3:{i:0;s:1:\"a\";i:1;s:1:\"b\";i:2;s:1:\"c\";}");
        assertThat(result).isEqualTo(List.of("a", "b", "c"));
    }

    // A gapped/re-ordered integer-keyed array is not the "0, 1, 2, ..." sequence, so it becomes a
    // map (stringified keys) instead of a list — same rule PhpArrayParser already applies for its
    // own array-literal syntax.
    @Test
    void parsesANonSequentialIntegerKeyedArrayAsAMapWithStringifiedKeys() {
        Object result = PhpSerializeParser.parse("a:2:{i:5;s:1:\"x\";i:2;s:1:\"y\";}");
        assertThat(result).isEqualTo(Map.of("5", "x", "2", "y"));
    }

    @Test
    void parsesNestedArraysAndObjects() {
        Object result = PhpSerializeParser.parse(
                "a:2:{s:4:\"tags\";a:2:{i:0;s:4:\"json\";i:1;s:3:\"php\";}s:4:\"meta\";a:1:{s:2:\"ok\";b:1;}}");
        assertThat(result).isEqualTo(Map.of("tags", List.of("json", "php"), "meta", Map.of("ok", true)));
    }

    @Test
    void parsesNullFalseAndDouble() {
        assertThat(PhpSerializeParser.parse("N;")).isNull();
        assertThat(PhpSerializeParser.parse("b:0;")).isEqualTo(Boolean.FALSE);
        assertThat(PhpSerializeParser.parse("d:3.14;")).isEqualTo(3.14);
    }

    // The inverse of PhpSerializeWriterTest's own multi-byte case — verified against the same real
    // standalone Java harness first, confirming the codePoint-based byte-length scan (not raw char
    // count) correctly consumes a 4-byte emoji sequence (a Java surrogate pair) without splitting
    // it.
    @Test
    void parsesAMultiByteStringByItsUtf8ByteLengthNotJavaCharCount() {
        assertThat(PhpSerializeParser.parse("s:5:\"café\";")).isEqualTo("café");
        assertThat(PhpSerializeParser.parse("s:6:\"Hi👋\";")).isEqualTo("Hi👋");
    }

    @Test
    void roundTripsThroughPhpSerializeWriter() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var original = mapper.readTree("{\"name\":\"DevKnowledge\",\"active\":true,\"count\":47}");
        String serialized = PhpSerializeWriter.write(original);
        Object parsed = PhpSerializeParser.parse(serialized);
        assertThat(parsed).isEqualTo(Map.of("name", "DevKnowledge", "active", true, "count", 47L));
    }

    @Test
    void malformedInputThrowsPhpSerializeParseExceptionWithALineColumnLocation() {
        assertThatThrownBy(() -> PhpSerializeParser.parse("a:1:{"))
                .isInstanceOf(PhpSerializeParser.PhpSerializeParseException.class)
                .hasMessageContaining("(line 1, column");
    }

    @Test
    void trailingContentAfterTheValueIsRejected() {
        assertThatThrownBy(() -> PhpSerializeParser.parse("i:1;garbage"))
                .isInstanceOf(PhpSerializeParser.PhpSerializeParseException.class);
    }
}
