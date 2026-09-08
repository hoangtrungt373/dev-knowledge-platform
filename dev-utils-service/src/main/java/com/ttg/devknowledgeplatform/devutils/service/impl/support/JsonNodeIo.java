package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.devutils.exception.ParsingExceptionMessages;

/**
 * Two small static helpers factoring out a pair of blocks that used to be copy-pasted, near
 * character-for-character, across 5-6 operation classes ({@code JsonFormatOperation},
 * {@code YamlToJsonOperation}, {@code JsonToCsvOperation}, {@code JsonToPhpOperation},
 * {@code CsvToJsonOperation}, {@code PhpToJsonOperation}): "parse JSON, and on failure throw a
 * {@link BusinessException} carrying {@link ParsingExceptionMessages}'s own cleaned-up message,"
 * and "serialize a value either pretty-printed or, with {@code minify}, compact/single-line." Both
 * are thin wrappers, not a new abstraction over how each operation actually behaves — every caller
 * still owns its own error code (and, for {@link #readTree}, whatever it does with the parsed
 * {@link JsonNode} afterward), so this doesn't force operations with genuinely different shapes
 * through one common method the way {@code service.DevUtilOperation}'s own Javadoc warns against
 * for the operations themselves; it only removes literal duplication of Jackson call-and-catch
 * boilerplate, which every caller already did identically.
 */
public final class JsonNodeIo {

    private JsonNodeIo() {
    }

    /**
     * Parses {@code input} as a JSON (or, via a {@link com.fasterxml.jackson.dataformat.yaml.YAMLMapper}
     * — itself an {@link ObjectMapper} subtype — YAML) document into a tree. On a parse failure,
     * throws a {@link BusinessException} against {@code errorCode} carrying
     * {@link ParsingExceptionMessages#friendlyMessage}'s own cleaned-up detail — the {@code
     * (Object)} cast on that call is load-bearing, not decorative; see any caller's own previous
     * inline comment (now here) for why a plain {@code String} argument would silently skip
     * {@link ErrorCode#formatMessage}'s template application.
     *
     * @throws BusinessException wrapping {@code errorCode} when {@code input} isn't parseable
     */
    public static JsonNode readTree(ObjectMapper mapper, String input, ErrorCode errorCode) {
        try {
            return mapper.readTree(input);
        } catch (JsonProcessingException e) {
            throw new BusinessException(errorCode, (Object) ParsingExceptionMessages.friendlyMessage(e));
        }
    }

    /**
     * Serializes {@code value} via {@code mapper} — compact/single-line when {@code minify} is
     * {@code true}, pretty-printed otherwise. On the rare failure (every caller only ever passes a
     * value tree it already built/validated itself, so this is a defensive catch, not an expected
     * path), throws a {@link BusinessException} against {@code errorCode} carrying the raw
     * {@link JsonProcessingException#getOriginalMessage()} — not run through
     * {@link ParsingExceptionMessages}, since that helper exists specifically to clean up a
     * *parse* failure's diagnostics, not a serialization one.
     *
     * @throws BusinessException wrapping {@code errorCode} on a serialization failure
     */
    public static String write(ObjectMapper mapper, Object value, boolean minify, ErrorCode errorCode) {
        try {
            return minify
                    ? mapper.writeValueAsString(value)
                    : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(errorCode, (Object) e.getOriginalMessage());
        }
    }
}
