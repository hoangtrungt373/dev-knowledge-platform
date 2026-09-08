package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.config.YamlMapperConfig;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JsonToYamlOperationTest {

    private static final String VALID_JSON = "{\"name\":\"Alice\",\"items\":[1,2,3]}";

    private ObjectMapper objectMapper;
    private YAMLMapper yamlMapper;
    private JsonToYamlOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // The real YamlMapperConfig#yamlMapper() bean, not a bare YAMLMapper.builder().build() —
        // a plain builder call here would silently drift from the production bean's own
        // YAMLGenerator.Feature overrides (see that class's own Javadoc), the same
        // test-vs-production mismatch this module has already been caught drifting on elsewhere.
        yamlMapper = new YamlMapperConfig().yamlMapper();
        operation = new JsonToYamlOperation(objectMapper, yamlMapper);
    }

    @Test
    void convertsJsonToYamlPreservingStructure() throws Exception {
        String result = operation.execute(VALID_JSON);

        assertThat(yamlMapper.readTree(result)).isEqualTo(objectMapper.readTree(VALID_JSON));
    }

    @Test
    void producesConventionalYamlStyleNotJacksonsOwnDefault() {
        // No leading "---" document marker, a plain string left unquoted when safe, and a block
        // sequence's "-" indicator indented 2 spaces under its parent key — see
        // YamlMapperConfig's own Javadoc for the 3 ways YAMLMapper.builder().build()'s stock
        // defaults diverge from this (a real bug, reported directly against this exact payload,
        // not a style choice).
        String input = "{\"project\":\"Vui Coding\",\"version\":2,"
                + "\"features\":[\"tools\",\"launch board\"],\"active\":true}";

        String result = operation.execute(input);

        assertThat(result).isEqualTo(
                "project: Vui Coding\n"
                        + "version: 2\n"
                        + "features:\n"
                        + "  - tools\n"
                        + "  - launch board\n"
                        + "active: true\n"
        );
    }

    @Test
    void rejectsMalformedJsonWithInvalidJsonErrorCode() {
        assertThatThrownBy(() -> operation.execute("{not valid json"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JSON));
    }
}
