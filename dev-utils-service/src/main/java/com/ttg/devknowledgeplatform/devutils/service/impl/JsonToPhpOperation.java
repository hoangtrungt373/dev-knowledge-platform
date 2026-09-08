package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.PhpArrayWriter;

import lombok.RequiredArgsConstructor;

/**
 * Converts a raw JSON value into a PHP array literal (wrapped in a {@code <?php return ...;}
 * snippet), one clause per line, or, with {@code minify}, a compact single-line form — the same
 * pretty/minify choice {@code JsonFormatOperation} already applies to its own output, mirrored
 * here for the PHP side (a PHP array literal has just as meaningful a "compact" form as JSON does,
 * unlike YAML/CSV — see {@code JsonToYamlOperation}/{@code JsonToCsvOperation}'s own Javadoc for
 * why those two have no minify concept).
 *
 * <p>Delegates the actual rendering to {@link PhpArrayWriter}. Reuses
 * {@code DevUtilsErrorCode.INVALID_JSON} rather than getting its own error code — this operation's
 * input is JSON either way, and any valid JSON value (object, array, or scalar) can always become
 * a PHP array/scalar, so the only possible failure is a genuine JSON syntax error, same as
 * {@code JsonFormatOperation}'s own failure path.
 */
@Component
@RequiredArgsConstructor
public class JsonToPhpOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_JSON} when
     *                           {@code input} isn't valid JSON
     */
    public String execute(String input, boolean minify) {
        JsonNode root = JsonNodeIo.readTree(objectMapper, input, DevUtilsErrorCode.INVALID_JSON);
        return PhpArrayWriter.write(root, minify);
    }
}
