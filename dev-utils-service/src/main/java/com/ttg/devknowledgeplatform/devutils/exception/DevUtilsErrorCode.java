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
 * since its input is JSON either way. {@code INVALID_HEX} is the same shape once more, for
 * {@code HexToAsciiOperation} — backed by the JDK's own {@link java.util.HexFormat#parseHex}
 * (an odd digit count or a non-hex character), unlike {@code AsciiToHexOperation}, which — like
 * {@code Base64EncodeOperation} — has no invalid-input concept at all (every string has a valid
 * hex representation). {@code INVALID_JWT} is the same shape once more, for
 * {@code JwtDebuggerOperation} — backed by a real structural check (exactly 3 dot-separated
 * segments, the JWS Compact Serialization shape RFC 7515 §3.1 requires) plus the JDK's own
 * {@link java.util.Base64.Decoder} (URL-safe alphabet) and this module's own JSON parser for the
 * header/payload segments; the one operation in this enum whose failure message can come from
 * either of two genuinely different validators depending on which segment/step actually failed.
 * {@code INVALID_REGEX}/{@code REGEX_TIMEOUT} back {@code RegexTesterOperation}'s own two
 * distinct failure modes — a pattern that fails to compile at all (a genuine
 * {@link java.util.regex.PatternSyntaxException}), vs. one that compiles fine but takes too long
 * to evaluate against the given text (see that class's own Javadoc for why a public,
 * unauthenticated regex endpoint needs an explicit timeout guard against catastrophic
 * backtracking — confirmed as a real, not just theoretical, risk against this JDK's own
 * {@code java.util.regex} engine before this was built). {@code INVALID_URL} backs
 * {@code UrlParserOperation}'s own real failure path — a {@link java.net.URISyntaxException} from
 * {@link java.net.URI}, or a syntactically valid URI reference that isn't absolute/has no host
 * (e.g. a bare path, or a {@code mailto:} address) — genuinely distinct from
 * {@code INVALID_URL_ENCODING} above, which is about percent-encoding syntax specifically, not a
 * URL's overall structure. {@code INVALID_CRON} backs {@code CronParserOperation}'s own real
 * failure path — the wrong field count (not exactly 5), or any one field's own syntax malformed
 * or out of range (a non-numeric/unrecognized-alias token, a non-positive step, or a value outside
 * that field's valid range). {@code DIFF_INPUT_TOO_LARGE} backs {@code TextDiffOperation}'s own
 * real failure path — not a genuinely *invalid* input (any two texts have some well-defined diff),
 * but a resource-exhaustion guard: the line-by-line LCS algorithm it uses is {@code O(n×m)} in
 * line count, so either text having too many lines is rejected outright before the expensive
 * comparison ever runs, rather than attempting it and hoping it finishes in time (see that class's
 * own Javadoc for why this is a cleaner mitigation than {@code RegexTesterOperation}'s own
 * best-effort timeout — the input size here is known and cheap to check *before* doing the
 * expensive work, unlike a regex match, which can't be judged expensive without already running
 * it).
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
    INVALID_PHP_SERIALIZED("DEVUTILS_008", "Invalid PHP serialized data: {0}", HttpStatus.BAD_REQUEST),
    INVALID_HEX("DEVUTILS_009", "Invalid hex data: {0}", HttpStatus.BAD_REQUEST),
    INVALID_JWT("DEVUTILS_010", "Invalid JWT: {0}", HttpStatus.BAD_REQUEST),
    INVALID_REGEX("DEVUTILS_011", "Invalid regular expression: {0}", HttpStatus.BAD_REQUEST),
    REGEX_TIMEOUT("DEVUTILS_012", "Regular expression evaluation timed out: {0}", HttpStatus.BAD_REQUEST),
    INVALID_URL("DEVUTILS_013", "Invalid URL: {0}", HttpStatus.BAD_REQUEST),
    INVALID_CRON("DEVUTILS_014", "Invalid cron expression: {0}", HttpStatus.BAD_REQUEST),
    DIFF_INPUT_TOO_LARGE("DEVUTILS_015", "Text is too large to diff: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevUtilsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
