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
        super(errorCode.getMessage());
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
    
    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = null;
    }
    
    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }
}
