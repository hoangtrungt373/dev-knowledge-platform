package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JsonToCsvOperationTest {

    private final JsonToCsvOperation operation = new JsonToCsvOperation(new ObjectMapper());

    @Test
    void convertsAnArrayOfFlatObjectsToCsvWithAHeaderRow() {
        String result = operation.execute("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");

        assertThat(result).isEqualToNormalizingNewlines("id,name\n1,Alice\n2,Bob\n");
    }

    @Test
    void treatsASingleObjectAsOneRow() {
        String result = operation.execute("{\"id\":1,\"name\":\"Alice\"}");

        assertThat(result).isEqualToNormalizingNewlines("id,name\n1,Alice\n");
    }

    @Test
    void unionsColumnsAcrossHeterogeneousRowsLeavingBlankCellsForMissingFields() {
        String result = operation.execute("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"email\":\"bob@x.com\"}]");

        assertThat(result).isEqualToNormalizingNewlines("id,name,email\n1,Alice,\n2,,bob@x.com\n");
    }

    @Test
    void writesNestedValuesAsCompactJsonInTheCell() {
        String result = operation.execute("[{\"id\":1,\"tags\":[\"a\",\"b\"]}]");

        // The cell's raw content is the compact JSON string `["a","b"]` — but since that content
        // itself contains commas and double quotes, standard CSV escaping (RFC 4180) wraps the
        // whole cell in quotes and doubles each embedded quote, producing `"[""a"",""b""]"` in the
        // actual CSV output rather than the raw JSON substring verbatim.
        assertThat(result).contains("\"[\"\"a\"\",\"\"b\"\"]\"");
    }

    @Test
    void malformedJsonThrowsBusinessExceptionWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("not valid json"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_JSON);
    }

    @Test
    void jsonNotShapedAsRowsThrowsBusinessException() {
        assertThatThrownBy(() -> operation.execute("[1, 2, 3]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_JSON);

        assertThatThrownBy(() -> operation.execute("\"just a string\""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void emptyArrayProducesEmptyOutput() {
        assertThat(operation.execute("[]")).isEmpty();
    }
}
