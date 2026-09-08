package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class CsvToJsonOperationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CsvToJsonOperation operation = new CsvToJsonOperation(objectMapper);

    @Test
    void convertsCsvRowsIntoAJsonArrayOfObjectsKeyedByTheHeaderRow() throws Exception {
        String result = operation.execute("id,name\n1,Alice\n2,Bob\n", true);

        JsonNode node = objectMapper.readTree(result);
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(2);
        assertThat(node.get(0).get("id").asText()).isEqualTo("1");
        assertThat(node.get(0).get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void everyValueComesBackAsAJsonStringNeverAnInferredNumber() throws Exception {
        String result = operation.execute("code\n007\n", true);

        JsonNode node = objectMapper.readTree(result);
        assertThat(node.get(0).get("code").isTextual()).isTrue();
        assertThat(node.get(0).get("code").asText()).isEqualTo("007");
    }

    @Test
    void producesPrettyPrintedOutputByDefault() {
        String result = operation.execute("id,name\n1,Alice\n", false);

        assertThat(result).contains("\n");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute("id,name\n1,Alice\n", true);

        assertThat(result).doesNotContain("\n  ");
    }

    @Test
    void malformedCsvThrowsBusinessExceptionWithInvalidCsvErrorCode() {
        // A row with more columns than the header is a genuine, common CSV mistake Jackson's
        // CsvMapper rejects outright.
        assertThatThrownBy(() -> operation.execute("id,name\n1,Alice,extra\n", true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_CSV);
    }
}
