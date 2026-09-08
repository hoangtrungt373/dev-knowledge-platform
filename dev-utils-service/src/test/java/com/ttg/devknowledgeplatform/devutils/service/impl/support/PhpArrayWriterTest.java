package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class PhpArrayWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesAJsonObjectAsAPrettyPrintedAssociativeArray() throws Exception {
        JsonNode node = objectMapper.readTree("{\"name\":\"Vui Coding\",\"active\":true,\"tools\":[\"JSON\",\"JWT\"]}");

        String result = PhpArrayWriter.write(node, false);

        // A real bug, reported directly against this exact payload: a blank line belongs between
        // "<?php" and "return" (the standard convention this snippet's own shape evokes) — see
        // PhpArrayWriter#write's own comment for why.
        assertThat(result).isEqualTo(
                "<?php\n"
                        + "\n"
                        + "return [\n"
                        + "  'name' => 'Vui Coding',\n"
                        + "  'active' => true,\n"
                        + "  'tools' => [\n"
                        + "    'JSON',\n"
                        + "    'JWT'\n"
                        + "  ]\n"
                        + "];"
        );
    }

    @Test
    void writesAJsonArrayAsAPlainListWithNoKeys() throws Exception {
        JsonNode node = objectMapper.readTree("[\"JSON\",\"JWT\"]");

        String result = PhpArrayWriter.write(node, false);

        assertThat(result).isEqualTo("<?php\n\nreturn [\n  'JSON',\n  'JWT'\n];");
    }

    @Test
    void minifyProducesACompactSingleLineForm() throws Exception {
        JsonNode node = objectMapper.readTree("{\"name\":\"Vui Coding\",\"active\":true,\"tools\":[\"JSON\",\"JWT\"]}");

        String result = PhpArrayWriter.write(node, true);

        assertThat(result).isEqualTo("<?php return ['name'=>'Vui Coding','active'=>true,'tools'=>['JSON','JWT']];");
    }

    @Test
    void escapesSingleQuotesAndBackslashesInStringValues() throws Exception {
        JsonNode node = objectMapper.readTree("[\"it's\", \"a\\\\b\"]");

        String result = PhpArrayWriter.write(node, true);

        assertThat(result).contains("'it\\'s'").contains("'a\\\\b'");
    }

    @Test
    void writesAnEmptyObjectOrArrayWithoutANewline() throws Exception {
        assertThat(PhpArrayWriter.write(objectMapper.readTree("{}"), false)).isEqualTo("<?php\n\nreturn [];");
        assertThat(PhpArrayWriter.write(objectMapper.readTree("[]"), false)).isEqualTo("<?php\n\nreturn [];");
    }

    @Test
    void roundTripsThroughPhpArrayParser() throws Exception {
        JsonNode node = objectMapper.readTree("{\"name\":\"Vui Coding\",\"active\":true,\"tools\":[\"JSON\",\"JWT\"]}");

        String phpSource = PhpArrayWriter.write(node, false);
        Object parsedBack = PhpArrayParser.parse(phpSource);

        assertThat(parsedBack).isEqualTo(java.util.Map.of(
                "name", "Vui Coding",
                "active", true,
                "tools", java.util.List.of("JSON", "JWT")
        ));
    }
}
