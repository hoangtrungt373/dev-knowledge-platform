package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;

import lombok.RequiredArgsConstructor;

/**
 * Validates a raw JSON string and re-serializes it pretty-printed (or, with {@code minify},
 * compact/single-line). Doubles as the "JSON validate" operation — a {@link BusinessException}
 * means invalid input, success means both valid and formatted in one pass.
 */
@Component
@RequiredArgsConstructor
public class JsonFormatOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    public String execute(String input, boolean minify) {
        try {
            JsonNode node = objectMapper.readTree(input);
            return minify
                    ? objectMapper.writeValueAsString(node)
                    : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JSON, e.getMessage());
        }
    }
}
