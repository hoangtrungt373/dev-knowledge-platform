package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class YamlToJsonOperationTest {

    private static final String VALID_YAML = "name: Alice\nitems:\n  - 1\n  - 2\n  - 3\n";

    private ObjectMapper objectMapper;
    private YAMLMapper yamlMapper;
    private YamlToJsonOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        yamlMapper = YAMLMapper.builder().build();
        operation = new YamlToJsonOperation(objectMapper, yamlMapper);
    }

    @Test
    void prettyPrintsByDefault() throws Exception {
        String result = operation.execute(VALID_YAML, false);

        assertThat(result).contains("\n");
        assertThat(objectMapper.readTree(result)).isEqualTo(yamlMapper.readTree(VALID_YAML));
    }

    @Test
    void producesCompactSingleLineJsonWhenMinified() throws Exception {
        String result = operation.execute(VALID_YAML, true);

        assertThat(result).doesNotContain("\n");
        assertThat(objectMapper.readTree(result)).isEqualTo(yamlMapper.readTree(VALID_YAML));
    }

    @Test
    void rejectsMalformedYamlWithInvalidYamlErrorCode() {
        assertThatThrownBy(() -> operation.execute("key: [unterminated", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_YAML));
    }
}
