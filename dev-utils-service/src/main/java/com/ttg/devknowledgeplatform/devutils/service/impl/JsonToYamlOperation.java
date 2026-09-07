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
 * {@link YamlToJsonOperation} follows in the other direction. Both mappers are Spring-managed
 * beans — see {@code config.YamlMapperConfig}'s Javadoc for why {@code yamlMapper} is a bean rather
 * than a plain {@code new YAMLMapper()} field.
 *
 * <p><b>No {@code minify} parameter</b> — {@code jackson-dataformat-yaml} has no supported
 * single-line/flow-style toggle, so there's no safe way to produce a "compact" YAML document at
 * all; output is always the same block-style YAML. See {@code service.DevUtilOperation}'s own
 * Javadoc for why this operation isn't forced to accept (and ignore) a parameter it has no use
 * for.
 */
@Component
@RequiredArgsConstructor
public class JsonToYamlOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;
    private final YAMLMapper yamlMapper;

    public String execute(String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            return yamlMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JSON, e.getMessage());
        }
    }
}
