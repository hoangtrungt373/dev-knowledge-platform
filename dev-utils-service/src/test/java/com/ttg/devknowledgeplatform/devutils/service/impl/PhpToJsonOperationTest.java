package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class PhpToJsonOperationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PhpToJsonOperation operation = new PhpToJsonOperation(objectMapper);

    @Test
    void convertsAnAssociativeArrayToPrettyPrintedJson() throws Exception {
        String result = operation.execute(
                "['name' => 'Vui Coding', 'active' => true, 'tools' => ['JSON', 'JWT']]", false);

        JsonNode node = objectMapper.readTree(result);
        assertThat(node.get("name").asText()).isEqualTo("Vui Coding");
        assertThat(node.get("active").asBoolean()).isTrue();
        assertThat(node.get("tools").get(0).asText()).isEqualTo("JSON");
        assertThat(result).contains("\n");
    }

    @Test
    void producesCompactJsonWhenMinified() {
        String result = operation.execute("['name' => 'Vui Coding']", true);

        assertThat(result).isEqualTo("{\"name\":\"Vui Coding\"}");
    }

    @Test
    void malformedPhpThrowsBusinessExceptionWithInvalidPhpErrorCode() {
        assertThatThrownBy(() -> operation.execute("['a', 'b'", false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_PHP);
    }
}
