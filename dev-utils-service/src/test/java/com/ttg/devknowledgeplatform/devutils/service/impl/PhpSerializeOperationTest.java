package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class PhpSerializeOperationTest {

    private final PhpSerializeOperation operation = new PhpSerializeOperation(new ObjectMapper());

    @Test
    void serializesTheExactReportedExample() {
        assertThat(operation.execute("{\"name\":\"DevKnowledge\",\"active\":true,\"count\":47}"))
                .isEqualTo("a:3:{s:4:\"name\";s:12:\"DevKnowledge\";s:6:\"active\";b:1;s:5:\"count\";i:47;}");
    }

    @Test
    void serializesAJsonArray() {
        assertThat(operation.execute("[\"a\",\"b\"]")).isEqualTo("a:2:{i:0;s:1:\"a\";i:1;s:1:\"b\";}");
    }

    @Test
    void malformedJsonThrowsBusinessExceptionWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("not valid json"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_JSON);
    }
}
