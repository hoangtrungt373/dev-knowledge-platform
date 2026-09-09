package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class PhpUnserializeOperationTest {

    private final PhpUnserializeOperation operation = new PhpUnserializeOperation(new ObjectMapper());

    @Test
    void unserializesTheExactReportedExampleBackIntoJson() {
        // Pretty-printed via the module's own ConventionalJsonPrettyPrinter — ": " (space after
        // the colon only), not Jackson's own stock "default pretty printer" spacing.
        String json = operation.execute("a:3:{s:4:\"name\";s:12:\"DevKnowledge\";s:6:\"active\";b:1;s:5:\"count\";i:47;}");
        assertThat(json).contains("\"name\": \"DevKnowledge\"", "\"active\": true", "\"count\": 47");
    }

    @Test
    void unserializesASequentialArrayBackIntoAJsonArray() {
        assertThat(operation.execute("a:2:{i:0;s:1:\"a\";i:1;s:1:\"b\";}")).contains("[", "\"a\"", "\"b\"", "]");
    }

    @Test
    void malformedSerializedDataThrowsBusinessExceptionWithInvalidPhpSerializedErrorCode() {
        assertThatThrownBy(() -> operation.execute("a:1:{"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_PHP_SERIALIZED);
    }
}
