package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ConventionalJsonPrettyPrinterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String prettyPrint(Object value) throws Exception {
        return objectMapper.writer(new ConventionalJsonPrettyPrinter()).writeValueAsString(value);
    }

    @Test
    void writesNoSpaceBeforeTheColonUnlikeJacksonsOwnDefault() throws Exception {
        String result = prettyPrint(objectMapper.readTree("{\"name\":\"Alice\"}"));

        assertThat(result).contains("\"name\": \"Alice\"").doesNotContain("\"name\" :");
    }

    @Test
    void indentsArrayElementsOnePerLineLikeAnObjectsFields() throws Exception {
        String result = prettyPrint(objectMapper.readTree("[1,2,3]"));

        assertThat(result).isEqualTo("[\n  1,\n  2,\n  3\n]");
    }

    @Test
    void collapsesAnEmptyArrayToNoInnerSpace() throws Exception {
        String result = prettyPrint(objectMapper.readTree("[]"));

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void collapsesAnEmptyObjectToNoInnerSpace() throws Exception {
        String result = prettyPrint(objectMapper.readTree("{}"));

        assertThat(result).isEqualTo("{}");
    }

    @Test
    void reproducesTheExactReportedMismatch() throws Exception {
        // The concrete case this class was written to fix — a real payload where Jackson's own
        // default pretty printer produced `"key" : value`, single-line `[ "a", "b" ]` arrays, and
        // `[ ]` for an empty array, none of which match what a mainstream JSON formatter produces.
        String input = "{\"errorsList\":[{\"code\":\"E1\"}],\"warningsList\":[]}";

        String result = prettyPrint(objectMapper.readTree(input));

        assertThat(result).isEqualTo(
                "{\n"
                        + "  \"errorsList\": [\n"
                        + "    {\n"
                        + "      \"code\": \"E1\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"warningsList\": []\n"
                        + "}"
        );
    }
}
