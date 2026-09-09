package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.PhpSerializeWriter;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw JSON value into PHP's own {@code serialize()} textual format — {@code a:N:{...}}
 * for an object/array, {@code s:L:"..."} for a string, {@code i:N;}/{@code d:N;} for a number,
 * {@code b:0;}/{@code b:1;} for a boolean, {@code N;} for null. Genuinely different from this
 * module's own PHP <i>array-literal</i> converters ({@link JsonToPhpOperation}/
 * {@link PhpToJsonOperation}) — see {@code service.impl.support.PhpSerializeWriter}'s own Javadoc
 * for the full format and why it's a distinct thing from a PHP array literal.
 *
 * <p>Delegates the actual rendering to {@link PhpSerializeWriter}. Reuses
 * {@code DevUtilsErrorCode.INVALID_JSON} rather than getting its own error code, the same choice
 * {@link JsonToPhpOperation} already makes — this operation's input is JSON either way, and any
 * valid JSON value can always become PHP's serialize format, so the only possible failure is a
 * genuine JSON syntax error.
 */
@Component
@RequiredArgsConstructor
public class PhpSerializeOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_JSON} when
     *                           {@code input} isn't valid JSON
     */
    public String execute(String input) {
        JsonNode root = JsonNodeIo.readTree(objectMapper, input, DevUtilsErrorCode.INVALID_JSON);
        return PhpSerializeWriter.write(root);
    }
}
