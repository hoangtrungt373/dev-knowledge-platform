package com.ttg.devknowledgeplatform.devutils.exception;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Turns a Jackson {@link JsonProcessingException} into a clean, single-line message safe to hand
 * back to an API caller. {@link JsonProcessingException#getMessage()} embeds Jackson's own
 * parser-internals diagnostics ({@code StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION},
 * {@code [Source: REDACTED (...)]}) — sometimes twice over, since a handful of error shapes (e.g.
 * an unclosed object/array) bake a {@code "(start marker at [Source: ...])"} clause directly into
 * the exception's own original message, on top of the second {@code [Source: ...]} reference
 * {@code getMessage()}'s own wrapper appends for the failure location itself. None of that means
 * anything to a caller, and it isn't `MessageFormat`-templated by this module's own
 * {@link DevUtilsErrorCode#INVALID_JSON}/{@link DevUtilsErrorCode#INVALID_YAML} either — see this
 * class's call sites for why that template previously never actually applied.
 *
 * <p>{@code YamlToJsonOperation}'s {@code YAMLMapper} wraps SnakeYAML's own parser exceptions into
 * this same Jackson type, but SnakeYAML bakes its *own* {@code "in '<name>', line N, column M:"}
 * location trailer (plus a source-line snippet and a {@code ^} pointer) directly into the original
 * message text — a different shape from Jackson's own {@code "(start marker at [Source: ...])"},
 * so both are stripped here rather than assuming only one can occur.
 *
 * <p>The real line/column is never lost — it's re-appended from {@link
 * JsonProcessingException#getLocation()} (a structured {@link JsonLocation}, not string-parsed)
 * once the noise is gone, so a caller still gets a precise {@code "<message> (line N, column M)"}.
 */
public final class ParsingExceptionMessages {

    private ParsingExceptionMessages() {
    }

    public static String friendlyMessage(JsonProcessingException e) {
        String cleaned = e.getOriginalMessage()
                // Jackson's own inline location clause on a structural error (an unclosed
                // object/array references back to where it started).
                .replaceAll("\\s*\\(start marker at \\[Source:[^]]*]\\)", "")
                // SnakeYAML's own "in '<name>', line N, column M:" mark, plus the 2-line source
                // snippet + caret pointer it always renders directly beneath it — a YAML error can
                // carry two of these (a "context" mark and a "problem" mark), each interleaved with
                // the actual descriptive sentence, so this strips the mark *block* only, not the
                // surrounding text, and can match more than once.
                .replaceAll("\\n\\s*in '[^']*',\\s*line \\d+,\\s*column \\d+:\\n[^\\n]*\\n[^\\n]*\\^[^\\n]*\\n?", " ")
                .replaceAll("\\s+", " ")
                .trim();

        JsonLocation location = e.getLocation();
        if (location != null && location.getLineNr() >= 0 && location.getColumnNr() >= 0) {
            return cleaned + " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
        }
        return cleaned;
    }
}
