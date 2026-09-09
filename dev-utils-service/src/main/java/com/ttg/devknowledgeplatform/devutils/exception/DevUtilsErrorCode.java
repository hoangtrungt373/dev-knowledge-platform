package com.ttg.devknowledgeplatform.devutils.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code dev-utils-service}.
 *
 * <p>Format: MODULE_ACTION_ERROR. Example: {@code DEVUTILS_001}.
 *
 * <p>No {@code INVALID_HTML}/{@code INVALID_CSS}/{@code INVALID_LESS}/{@code INVALID_SCSS}/
 * {@code INVALID_JS}/{@code INVALID_SQL} code: {@code HtmlBeautifyOperation}'s jsoup parser is
 * deliberately lenient and never throws on malformed markup, and {@code CssOperation}/
 * {@code LessOperation}/{@code ScssOperation}/{@code JsOperation}/{@code SqlFormatOperation} all
 * delegate to a shared, equally lenient textual reformatter (see
 * {@code service.impl.support.CurlyBraceFormatter}/{@code SqlFormatter}'s own Javadoc for why
 * neither is a real grammar parser) — none of these six have an invalid-input failure path to name
 * here. {@code StringCaseOperation} is the same story for a different reason: it's a pure text
 * transform (split into words, re-case/re-join) with no notion of "invalid" input at all — any
 * string, however unusual, produces *some* result for every case variant.
 * {@code INVALID_XML}/{@code INVALID_CSV}/{@code INVALID_PHP} are the exceptions among the newer
 * operations: {@code XmlOperation}/{@code CsvToJsonOperation}/{@code PhpToJsonOperation} are
 * backed by real parsers (JAXP, Jackson's {@code CsvMapper}, and this module's own
 * {@code service.impl.support.PhpArrayParser}, respectively — see each class's own Javadoc), the
 * same "real parse, real invalid-input error" shape {@code INVALID_JSON}/{@code INVALID_YAML}
 * already establish. {@code JsonToCsvOperation}/{@code JsonToPhpOperation} both reuse
 * {@code INVALID_JSON} rather than getting their own code — their input is JSON either way, so a
 * failure there (a genuine JSON syntax error, or — for {@code JsonToCsvOperation} only — valid
 * JSON in a shape that can't become rows) is still honestly described as "Invalid JSON."
 * {@code INVALID_BASE64} is the same shape again, for {@code Base64DecodeOperation} — backed by
 * the JDK's own {@link java.util.Base64.Decoder}, a real (if strict) validator, unlike
 * {@code Base64EncodeOperation}, which — like {@code StringCaseOperation} — has no invalid-input
 * concept at all (every string has a valid encoding) and so needs no code of its own here.
 * {@code INVALID_URL_ENCODING} is the same shape once more, for {@code UrlDecodeOperation} —
 * backed by the JDK's own {@link java.net.URLDecoder}, unlike {@code UrlEncodeOperation}, which —
 * like {@code Base64EncodeOperation} — has no invalid-input concept at all.
 * {@code HtmlEntityEncodeOperation}/{@code HtmlEntityDecodeOperation} are the one pair in this
 * whole enum where *neither* direction has a code: encode has no invalid-input concept (same as
 * {@code Base64EncodeOperation}/{@code UrlEncodeOperation}), and decode is lenient by design — an
 * unrecognized {@code &...;} sequence is left untouched rather than rejected, the same "no notion
 * of invalid input" shape {@code StringCaseOperation} already establishes, just applied to a
 * decode direction instead of an encode one. {@code HashGeneratorOperation} is the same "no
 * invalid-input concept at all" story once more — every string has a valid SHA-1/256/384/512
 * digest — so it has no matching code either. {@code INVALID_PHP_SERIALIZED} is the same shape
 * as {@code INVALID_PHP} once more, for {@code PhpUnserializeOperation} — backed by this
 * module's own {@code service.impl.support.PhpSerializeParser}, a real validating parser for
 * PHP's {@code serialize()} textual format (genuinely distinct from the array-literal syntax
 * {@code PhpArrayParser}/{@code INVALID_PHP} cover) — unlike {@code PhpSerializeOperation}, which
 * — like {@code JsonToPhpOperation} — reuses {@code INVALID_JSON} instead of getting its own code,
 * since its input is JSON either way.
 */
@Getter
public enum DevUtilsErrorCode implements ErrorCode {

    INVALID_JSON("DEVUTILS_001", "Invalid JSON: {0}", HttpStatus.BAD_REQUEST),
    INVALID_YAML("DEVUTILS_002", "Invalid YAML: {0}", HttpStatus.BAD_REQUEST),
    INVALID_XML("DEVUTILS_003", "Invalid XML: {0}", HttpStatus.BAD_REQUEST),
    INVALID_CSV("DEVUTILS_004", "Invalid CSV: {0}", HttpStatus.BAD_REQUEST),
    INVALID_PHP("DEVUTILS_005", "Invalid PHP: {0}", HttpStatus.BAD_REQUEST),
    INVALID_BASE64("DEVUTILS_006", "Invalid Base64: {0}", HttpStatus.BAD_REQUEST),
    INVALID_URL_ENCODING("DEVUTILS_007", "Invalid URL encoding: {0}", HttpStatus.BAD_REQUEST),
    INVALID_PHP_SERIALIZED("DEVUTILS_008", "Invalid PHP serialized data: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevUtilsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
