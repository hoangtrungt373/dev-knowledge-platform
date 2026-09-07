package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JsonToYamlOperationTest {

    private static final String VALID_JSON = "{\"name\":\"Alice\",\"items\":[1,2,3]}";

    private ObjectMapper objectMapper;
    private YAMLMapper yamlMapper;
    private JsonToYamlOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        yamlMapper = YAMLMapper.builder().build();
        operation = new JsonToYamlOperation(objectMapper, yamlMapper);
    }

    @Test
    void convertsJsonToYamlPreservingStructure() throws Exception {
        String result = operation.execute(VALID_JSON);

        assertThat(yamlMapper.readTree(result)).isEqualTo(objectMapper.readTree(VALID_JSON));
    }

    @Test
    void rejectsMalformedJsonWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("{not valid json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JSON));
    }
}
