package com.ttg.devknowledgeplatform.devutils.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code dev-utils-service}.
 *
 * <p>Format: MODULE_ACTION_ERROR. Example: {@code DEVUTILS_001}.
 *
 * <p>No {@code INVALID_HTML} code: jsoup's parser is deliberately lenient and never throws on
 * malformed markup (see {@code HtmlBeautifyOperation}'s Javadoc), so there is no invalid-HTML
 * failure path to name here.
 */
@Getter
public enum DevUtilsErrorCode implements ErrorCode {

    INVALID_JSON("DEVUTILS_001", "Invalid JSON: {0}", HttpStatus.BAD_REQUEST),
    INVALID_YAML("DEVUTILS_002", "Invalid YAML: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevUtilsErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
