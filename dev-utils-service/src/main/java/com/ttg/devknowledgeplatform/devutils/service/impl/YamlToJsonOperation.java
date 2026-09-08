package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw YAML string to pretty-printed JSON. Both mappers are Spring-managed beans
 * ({@code objectMapper} from {@code infra}'s shared {@code JacksonConfig}, {@code yamlMapper} from
 * this module's own {@code config.YamlMapperConfig}) — see the latter's Javadoc for why a second,
 * YAML-side bean exists instead of a plain {@code new YAMLMapper()} field.
 */
@Component
@RequiredArgsConstructor
public class YamlToJsonOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;
    private final YAMLMapper yamlMapper;

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_YAML} when
     *                           {@code input} isn't valid YAML
     */
    public String execute(String input, boolean minify) {
        JsonNode node = JsonNodeIo.readTree(yamlMapper, input, DevUtilsErrorCode.INVALID_YAML);
        return JsonNodeIo.write(objectMapper, node, minify, DevUtilsErrorCode.INVALID_YAML);
    }
}
