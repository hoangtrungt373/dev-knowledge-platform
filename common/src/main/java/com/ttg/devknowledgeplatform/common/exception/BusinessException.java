package com.ttg.devknowledgeplatform.common.exception;

/**
 * Business logic exception
 * 
 * Use this for business rule violations (e.g., user not found, duplicate email)
 */
public class BusinessException extends ApiException {
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public BusinessException(ErrorCode errorCode, String message, Object... args) {
        super(errorCode, message, args);
    }
}
