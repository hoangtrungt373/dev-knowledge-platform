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
 * Converts a raw YAML string to pretty-printed JSON. {@code YAMLMapper} needs no customization
 * from {@code JacksonConfig}'s shared {@code ObjectMapper}, so it's a plain locally-constructed
 * field rather than a second Spring-managed {@code ObjectMapper} bean — reusing the injected one
 * (JSON-configured) purely for the output side keeps this module to a single customized mapper.
 */
@Component
@RequiredArgsConstructor
public class YamlToJsonOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    @Override
    public String execute(String input) {
        try {
            JsonNode node = yamlMapper.readTree(input);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_YAML, e.getMessage());
        }
    }
}
