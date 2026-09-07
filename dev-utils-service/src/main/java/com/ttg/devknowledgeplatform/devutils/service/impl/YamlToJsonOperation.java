package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.exception.ParsingExceptionMessages;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

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

    public String execute(String input, boolean minify) {
        try {
            JsonNode node = yamlMapper.readTree(input);
            return minify
                    ? objectMapper.writeValueAsString(node)
                    : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // (Object) cast forces the varargs BusinessException(ErrorCode, Object... templateArgs)
            // overload instead of BusinessException(ErrorCode, String message) — see
            // JsonFormatOperation's identical catch block for the full overload-resolution
            // reasoning, and ParsingExceptionMessages for why the message is cleaned up first.
            throw new BusinessException(DevUtilsErrorCode.INVALID_YAML, (Object) ParsingExceptionMessages.friendlyMessage(e));
        }
    }
}
