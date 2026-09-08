package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

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

    @Override
    public OperationGroup group() {
        return OperationGroup.FORMATTERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_JSON} when
     *                           {@code input} isn't valid JSON
     */
    public String execute(String input, boolean minify) {
        JsonNode node = JsonNodeIo.readTree(objectMapper, input, DevUtilsErrorCode.INVALID_JSON);
        return JsonNodeIo.write(objectMapper, node, minify, DevUtilsErrorCode.INVALID_JSON);
    }
}
