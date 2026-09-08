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

    @Test
    void doesNotQuoteAPlainValueThatOnlyContainsASpace() {
        // A real bug: Jackson's CsvMapper's own default ("loose") quoting check quotes a value
        // for containing any character below ASCII 45 (comma + 1) — including a plain space, not
        // just what RFC 4180 actually requires — so "JSON Formatter" used to render quoted with
        // no genuine reason to be. See this operation's own Javadoc for the STRICT_CHECK_FOR_QUOTING
        // fix.
        String result = operation.execute(
                "[{\"name\":\"JSON Formatter\",\"category\":\"Format\",\"free\":true},"
                        + "{\"name\":\"JWT Debugger\",\"category\":\"Inspect\",\"free\":true}]"
        );

        assertThat(result).isEqualToNormalizingNewlines(
                "name,category,free\nJSON Formatter,Format,true\nJWT Debugger,Inspect,true\n"
        );
    }

    @Test
    void stillQuotesAValueThatGenuinelyContainsACommaOrQuoteOrNewline() {
        // The other half of the STRICT_CHECK_FOR_QUOTING fix — confirms switching off the
        // overly-conservative default didn't also switch off RFC 4180's own real requirements.
        String result = operation.execute("[{\"note\":\"has a \\\"quote\\\", a comma, and a\\nnewline\"}]");

        assertThat(result).isEqualToNormalizingNewlines(
                "note\n\"has a \"\"quote\"\", a comma, and a\nnewline\"\n"
        );
    }
}
