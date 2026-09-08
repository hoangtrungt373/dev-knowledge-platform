package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JsonFormatOperationTest {

    private static final String VALID_JSON = "{\"name\":\"Alice\",\"items\":[1,2,3]}";

    private ObjectMapper objectMapper;
    private JsonFormatOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        operation = new JsonFormatOperation(objectMapper);
    }

    @Test
    void prettyPrintsByDefault() throws Exception {
        String result = operation.execute(VALID_JSON, false);

        assertThat(result).contains("\n");
        assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree(VALID_JSON));
    }

    @Test
    void prettyPrintUsesConventionalJsonStyleNotJacksonsOwnDefault() throws Exception {
        // No space before the colon, and an array's elements each get their own indented line —
        // see ConventionalJsonPrettyPrinter's own Javadoc for the 3 ways Jackson's own
        // writerWithDefaultPrettyPrinter() diverges from this (a real bug, not a style choice).
        String result = operation.execute(VALID_JSON, false);

        assertThat(result).isEqualTo(
                "{\n"
                        + "  \"name\": \"Alice\",\n"
                        + "  \"items\": [\n"
                        + "    1,\n"
                        + "    2,\n"
                        + "    3\n"
                        + "  ]\n"
                        + "}"
        );
    }

    @Test
    void producesCompactSingleLineOutputWhenMinified() throws Exception {
        String result = operation.execute(VALID_JSON, true);

        assertThat(result).doesNotContain("\n");
        assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree(VALID_JSON));
    }

    @Test
    void rejectsMalformedJsonWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("{not valid json", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JSON));
    }
}
