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
 * here. {@code INVALID_XML}/{@code INVALID_CSV} are the exceptions among the newer operations:
 * {@code XmlOperation}/{@code CsvToJsonOperation} are backed by real parsers (JAXP, Jackson's
 * {@code CsvMapper}, respectively — see each class's own Javadoc), the same "real parse, real
 * invalid-input error" shape {@code INVALID_JSON}/{@code INVALID_YAML} already establish.
 * {@code JsonToCsvOperation} reuses {@code INVALID_JSON} rather than getting its own code — its
 * input is JSON either way, so a failure there (a genuine JSON syntax error, or valid JSON in a
 * shape this operation can't turn into rows) is still honestly described as "Invalid JSON."
 */
@Getter
public enum DevUtilsErrorCode implements ErrorCode {

    INVALID_JSON("DEVUTILS_001", "Invalid JSON: {0}", HttpStatus.BAD_REQUEST),
    INVALID_YAML("DEVUTILS_002", "Invalid YAML: {0}", HttpStatus.BAD_REQUEST),
    INVALID_XML("DEVUTILS_003", "Invalid XML: {0}", HttpStatus.BAD_REQUEST),
    INVALID_CSV("DEVUTILS_004", "Invalid CSV: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevUtilsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
