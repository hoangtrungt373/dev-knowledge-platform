package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.PhpSerializeParser;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw PHP {@code serialize()}-format string back into JSON, always pretty-printed — no
 * minify option, the same "no distinct compact form" reasoning
 * {@link PhpSerializeOperation}'s own counterpart establishes for the other direction (unlike
 * {@link PhpToJsonOperation}, whose own JSON output does support a minify toggle — that
 * asymmetry was deliberately avoided here so a shared Minify control wouldn't affect only one of
 * this pair's two actions).
 *
 * <p>Delegates the actual parsing to {@code service.impl.support.PhpSerializeParser} (a real,
 * validating parser, not a lenient reformatter — see that class's own Javadoc for the full
 * grammar it supports, including its careful UTF-8 byte-length handling for {@code s:L:"..."}
 * strings). Real failure path, the same "real parse, real invalid-input error" shape
 * {@link PhpToJsonOperation} already establishes for the array-literal side — new
 * {@code DevUtilsErrorCode.INVALID_PHP_SERIALIZED}, backed by
 * {@code PhpSerializeParser.PhpSerializeParseException}'s own message (already carrying a
 * {@code "(line N, column M)"} location).
 */
@Component
@RequiredArgsConstructor
public class PhpUnserializeOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_PHP_SERIALIZED} when
     *                           {@code input} isn't a valid PHP {@code serialize()}-format string
     */
    public String execute(String input) {
        Object value;
        try {
            value = PhpSerializeParser.parse(input);
        } catch (PhpSerializeParser.PhpSerializeParseException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_PHP_SERIALIZED, (Object) e.getMessage());
        }
        return JsonNodeIo.write(objectMapper, value, false, DevUtilsErrorCode.INVALID_PHP_SERIALIZED);
    }
}
