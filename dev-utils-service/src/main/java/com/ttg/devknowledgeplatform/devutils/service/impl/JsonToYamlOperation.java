package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw JSON string to YAML. The input being validated is JSON (the source format), so a
 * malformed input throws {@link DevUtilsErrorCode#INVALID_JSON} — same convention
 * {@link YamlToJsonOperation} follows in the other direction.
 */
@Component
@RequiredArgsConstructor
public class JsonToYamlOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    @Override
    public String execute(String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            return yamlMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JSON, e.getMessage());
        }
    }
}
