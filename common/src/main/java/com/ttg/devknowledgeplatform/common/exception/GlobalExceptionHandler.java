package com.ttg.devknowledgeplatform.common.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.ttg.devknowledgeplatform.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // --- Custom exceptions ---

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpStatus status = ex.getErrorCode().getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("API error [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        } else {
            log.warn("API error [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        }
        return buildResponse(ex.getErrorCode().getCode(), status, ex.getMessage(), request, null);
    }

    // --- Validation exceptions ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed on {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, List<String>> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.computeIfAbsent(field, k -> new ArrayList<>()).add(error.getDefaultMessage());
        });
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.getCode(), HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, List<String>> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
            errors.computeIfAbsent(v.getPropertyPath().toString(), k -> new ArrayList<>()).add(v.getMessage())
        );
        return buildResponse(CommonErrorCode.VALIDATION_FAILED.getCode(), HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
    }

    // --- HTTP / MVC exceptions ---

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(CommonErrorCode.REQUEST_BODY_INVALID.getCode(), HttpStatus.BAD_REQUEST, "Malformed or missing request body", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not allowed on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(CommonErrorCode.REQUEST_METHOD_NOT_ALLOWED.getCode(), HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        return buildResponse(CommonErrorCode.REQUEST_PARAMETER_MISSING.getCode(), HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter '{}' on {}: {}", ex.getName(), request.getRequestURI(), ex.getMessage());
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return buildResponse(CommonErrorCode.VALIDATION_FIELD_INVALID.getCode(), HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("No handler found: {}", ex.getRequestURL());
        return buildResponse(CommonErrorCode.RESOURCE_NOT_FOUND.getCode(), HttpStatus.NOT_FOUND, "Endpoint not found: " + ex.getRequestURL(), request, null);
    }

    // --- Security exceptions ---

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(CommonErrorCode.AUTH_FORBIDDEN.getCode(), HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(CommonErrorCode.AUTH_UNAUTHORIZED.getCode(), HttpStatus.UNAUTHORIZED, "Authentication failed", request, null);
    }

    // --- Rate limiting ---

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded on {}", request.getRequestURI());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "60");
        ErrorResponse body = ErrorResponse.builder()
                .errorCode(ex.getErrorCode().getCode())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .errorMessage(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(body);
    }

    // --- External service exceptions ---

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailException ex, HttpServletRequest request) {
        log.error("Email service error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(CommonErrorCode.SERVER_EXTERNAL_SERVICE_ERROR.getCode(), HttpStatus.SERVICE_UNAVAILABLE, "Email service is currently unavailable", request, null);
    }

    // --- Fallback ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(CommonErrorCode.SERVER_INTERNAL_ERROR.getCode(), HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    // --- Builder helper ---

    private ResponseEntity<ErrorResponse> buildResponse(
            String code, HttpStatus status, String message,
            HttpServletRequest request, Map<String, List<String>> validationErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .errorCode(code)
                .status(status.value())
                .errorMessage(message)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}