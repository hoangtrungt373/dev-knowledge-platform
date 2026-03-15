package com.ttg.devknowledgeplatform.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard error response DTO
 * 
 * Used by GlobalExceptionHandler to return consistent error responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * Error code (e.g., "AUTH_001", "USER_NOT_FOUND")
     */
    private String errorCode;
    
    /**
     * HTTP status code (e.g., 400, 401, 404, 500)
     */
    private int status;
    
    /**
     * Error message (human-readable)
     */
    private String errorMessage;
    
    /**
     * Timestamp when error occurred
     */
    private Instant timestamp;
    
    /**
     * Request path that caused the error
     */
    private String path;
    
    /**
     * Validation errors (for @Valid failures)
     * Key: field name, Value: list of error messages
     */
    private Map<String, List<String>> validationErrors;
    
    /**
     * Additional error details (optional)
     */
    private Map<String, Object> details;
}
