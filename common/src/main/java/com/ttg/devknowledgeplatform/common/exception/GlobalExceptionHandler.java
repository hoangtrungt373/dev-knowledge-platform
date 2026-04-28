package com.ttg.devknowledgeplatform.common.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.ttg.devknowledgeplatform.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global Exception Handler
 * 
 * Handles all exceptions thrown by controllers and returns standardized error responses.
 * 
 * <h3>Exception Handling Priority:</h3>
 * <ol>
 *   <li>Custom exceptions (ApiException, BusinessException, ResourceNotFoundException)</li>
 *   <li>Spring Security exceptions (AuthenticationException, AccessDeniedException)</li>
 *   <li>Validation exceptions (MethodArgumentNotValidException, ConstraintViolationException)</li>
 *   <li>Spring MVC exceptions (NoHandlerFoundException, MethodArgumentTypeMismatchException)</li>
 *   <li>Generic exceptions (Exception)</li>
 * </ol>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle custom API exceptions
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        log.error("API Exception: {} - {}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode().getCode())
                .status(ex.getErrorCode().getHttpStatus().value())
                .errorMessage(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(errorResponse);
    }
    
    /**
     * Handle business exceptions (e.g., user not found, duplicate email)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business Exception: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode().getCode())
                .status(ex.getErrorCode().getHttpStatus().value())
                .errorMessage(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(errorResponse);
    }
    
    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource Not Found: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode().getCode())
                .status(HttpStatus.NOT_FOUND.value())
                .errorMessage(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle Spring Security authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication Exception: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.AUTH_UNAUTHORIZED.getCode())
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorMessage("Authentication failed: " + ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    /**
     * Handle Spring Security access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access Denied: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.AUTH_FORBIDDEN.getCode())
                .status(HttpStatus.FORBIDDEN.value())
                .errorMessage("Access denied: " + ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle bad credentials exceptions
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad Credentials: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.AUTH_UNAUTHORIZED.getCode())
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorMessage("Invalid credentials")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation Exception: {}", ex.getMessage());
        
        Map<String, List<String>> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.computeIfAbsent(fieldName, k -> new java.util.ArrayList<>()).add(errorMessage);
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_FAILED.getCode())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage("Validation failed")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle constraint violation exceptions (from @Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint Violation: {}", ex.getMessage());
        
        Map<String, List<String>> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            validationErrors.computeIfAbsent(fieldName, k -> new java.util.ArrayList<>()).add(errorMessage);
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.VALIDATION_FAILED.getCode())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage("Validation failed")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle method argument type mismatch (e.g., invalid path variable type)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Method Argument Type Mismatch: {}", ex.getMessage());
        
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                ex.getValue(), ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.REQUEST_PARAMETER_MISSING.getCode())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage(message)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle 404 Not Found (no handler found for request)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No Handler Found: {}", ex.getRequestURL());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                .status(HttpStatus.NOT_FOUND.value())
                .errorMessage("Endpoint not found: " + ex.getRequestURL())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal Argument: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.REQUEST_BODY_INVALID.getCode())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorMessage(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle all other exceptions (fallback)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ErrorCode.SERVER_INTERNAL_ERROR.getCode())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorMessage("An unexpected error occurred. Please try again later.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
