package com.ttg.devknowledgeplatform.common.exception;

import lombok.Getter;

/**
 * Base exception class for API exceptions
 * 
 * All custom exceptions should extend this class to ensure
 * consistent error handling and error code mapping.
 */
@Getter
public class ApiException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Object[] args;
    
    public ApiException(ErrorCode errorCode) {
        super(errorCode.formatMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public ApiException(ErrorCode errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * Builds the message from {@code errorCode}'s own template ({@link ErrorCode#formatMessage})
     * instead of a call-site literal — use this when the error code's default message already has
     * the right shape (e.g. {@code "A category with name ''{0}'' already exists"}) and only needs
     * the runtime value(s) substituted in, rather than a bespoke string built by concatenation.
     */
    public ApiException(ErrorCode errorCode, Object[] templateArgs) {
        super(errorCode.formatMessage(templateArgs));
        this.errorCode = errorCode;
        this.args = templateArgs;
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.formatMessage(), cause);
        this.errorCode = errorCode;
        this.args = null;
    }
    
    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }
}
