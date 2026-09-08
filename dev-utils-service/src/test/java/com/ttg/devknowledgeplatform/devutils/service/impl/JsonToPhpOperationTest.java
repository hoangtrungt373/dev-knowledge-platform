package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JsonToPhpOperationTest {

    private final JsonToPhpOperation operation = new JsonToPhpOperation(new ObjectMapper());

    @Test
    void convertsAJsonObjectToAPrettyPrintedPhpArrayLiteral() {
        // The exact reported bug: a blank line belongs between "<?php" and "return" — see
        // PhpArrayWriter#write's own comment for why it was missing.
        String result = operation.execute("{\"name\":\"Vui Coding\",\"active\":true,\"tools\":[\"JSON\",\"JWT\"]}", false);

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
    void producesCompactSingleLinePhpWhenMinified() {
        String result = operation.execute("{\"name\":\"Vui Coding\"}", true);

        assertThat(result).isEqualTo("<?php return ['name'=>'Vui Coding'];");
    }

    @Test
    void malformedJsonThrowsBusinessExceptionWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("not valid json", false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_JSON);
    }
}
