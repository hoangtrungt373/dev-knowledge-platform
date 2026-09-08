package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.PhpArrayParser;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw PHP array literal — {@code [...]} or legacy {@code array(...)} — into JSON,
 * pretty-printed or (with {@code minify}) compact/single-line, the same pretty/minify choice
 * {@code JsonFormatOperation}/{@code YamlToJsonOperation} already apply to their own JSON output.
 *
 * <p>Delegates the actual parsing to {@link PhpArrayParser} (a real, validating parser, not a
 * lenient reformatter — see that class's own Javadoc for the full grammar/leniency rules it
 * supports, including tolerating a full {@code <?php ... ;} snippet, not just the bare array
 * literal). Real failure path, same "real parse, real invalid-input error" shape
 * {@code JsonFormatOperation} already establishes — {@code DevUtilsErrorCode.INVALID_PHP}
 * (backed by {@link PhpArrayParser.PhpParseException}, whose own message already carries a
 * {@code "(line N, column M)"} location).
 */
@Component
@RequiredArgsConstructor
public class PhpToJsonOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_PHP} when {@code input}
     *                           isn't a valid PHP array literal
     */
    public String execute(String input, boolean minify) {
        Object value;
        try {
            value = PhpArrayParser.parse(input);
        } catch (PhpArrayParser.PhpParseException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_PHP, (Object) e.getMessage());
        }
        return JsonNodeIo.write(objectMapper, value, minify, DevUtilsErrorCode.INVALID_PHP);
    }
}
