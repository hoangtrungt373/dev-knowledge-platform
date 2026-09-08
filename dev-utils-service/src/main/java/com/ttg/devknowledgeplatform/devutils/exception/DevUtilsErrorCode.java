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
 * {@code INVALID_JS} code: {@code HtmlBeautifyOperation}'s jsoup parser is deliberately lenient and
 * never throws on malformed markup, and {@code CssOperation}/{@code LessOperation}/
 * {@code ScssOperation}/{@code JsOperation} all delegate to the shared, equally lenient
 * {@code service.impl.support.CurlyBraceFormatter} (see that class's own Javadoc for why it's a
 * textual reformatter, not a real grammar parser) — none of these five have an invalid-input
 * failure path to name here. {@code INVALID_XML} is the one exception among the newer operations:
 * {@code XmlOperation} is backed by a real JAXP parser (see that class's own Javadoc), the same
 * "real parse, real invalid-input error" shape {@code INVALID_JSON}/{@code INVALID_YAML} already
 * establish.
 */
@Getter
public enum DevUtilsErrorCode implements ErrorCode {

    INVALID_JSON("DEVUTILS_001", "Invalid JSON: {0}", HttpStatus.BAD_REQUEST),
    INVALID_YAML("DEVUTILS_002", "Invalid YAML: {0}", HttpStatus.BAD_REQUEST),
    INVALID_XML("DEVUTILS_003", "Invalid XML: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevUtilsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
