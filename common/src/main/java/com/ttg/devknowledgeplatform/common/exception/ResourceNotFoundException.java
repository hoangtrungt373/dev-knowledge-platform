package com.ttg.devknowledgeplatform.common.exception;

/**
 * Resource not found exception
 * 
 * Use this when a requested resource doesn't exist
 */
public class ResourceNotFoundException extends BusinessException {
    
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ResourceNotFoundException(String resource, String identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("%s with identifier '%s' not found", resource, identifier));
    }
}
