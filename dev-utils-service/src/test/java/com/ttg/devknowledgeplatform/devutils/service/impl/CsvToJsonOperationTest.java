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
    void aCellThatIsExactlyTrueOrFalseBecomesARealJsonBoolean() throws Exception {
        // The one deliberate exception to "every value comes back as a string" above — added per
        // direct request, scoped to booleans only (not numbers) since this class's own Javadoc
        // explains why numeric inference stays out. Case-insensitive, and confirms a
        // similar-but-different string ("Yes") is correctly left untouched, not swept in too.
        String result = operation.execute(
                "name,category,free,verified\n"
                        + "JSON Formatter,Format,true,Yes\n"
                        + "JWT Debugger,Inspect,FALSE,yes\n",
                true
        );

        JsonNode node = objectMapper.readTree(result);
        assertThat(node.get(0).get("free").isBoolean()).isTrue();
        assertThat(node.get(0).get("free").asBoolean()).isTrue();
        assertThat(node.get(1).get("free").isBoolean()).isTrue();
        assertThat(node.get(1).get("free").asBoolean()).isFalse();
        assertThat(node.get(0).get("verified").isTextual()).isTrue();
        assertThat(node.get(0).get("verified").asText()).isEqualTo("Yes");
    }

    @Test
    void reproducesTheExactReportedExample() {
        String result = operation.execute(
                "name,category,free\nJSON Formatter,Format,true\nJWT Debugger,Inspect,true\n", false
        );

        assertThat(result).isEqualTo(
                "[\n"
                        + "  {\n"
                        + "    \"name\": \"JSON Formatter\",\n"
                        + "    \"category\": \"Format\",\n"
                        + "    \"free\": true\n"
                        + "  },\n"
                        + "  {\n"
                        + "    \"name\": \"JWT Debugger\",\n"
                        + "    \"category\": \"Inspect\",\n"
                        + "    \"free\": true\n"
                        + "  }\n"
                        + "]"
        );
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
