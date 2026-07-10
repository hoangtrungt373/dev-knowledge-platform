package com.ttg.devknowledgeplatform.common.exception;


import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Error codes owned by {@code common} and {@code api} — authentication/authorization, users,
 * validation, generic server/request/resource errors, and rate limiting. Domain-specific codes
 * live in their own module-local {@link ErrorCode} implementation instead: content (category/tag/
 * question-answer/article) in {@code content-service}'s {@code ContentErrorCode}, friend
 * management in {@code social-service}'s {@code FriendErrorCode}, AI/RAG in {@code ai-service}'s
 * {@code AiErrorCode}, and chat sessions in {@code api}'s own {@code ChatErrorCode}.
 *
 * Format: MODULE_ACTION_ERROR
 * Example: AUTH_TOKEN_INVALID, USER_NOT_FOUND
 */
@Getter
public enum CommonErrorCode implements ErrorCode {

    // Authentication & Authorization Errors (AUTH_*)
    AUTH_TOKEN_INVALID("AUTH_001", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_MISSING("AUTH_002", "Authorization token is required", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_003", "Token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHORIZED("AUTH_004", "Unauthorized access", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN("AUTH_005", "Access denied", HttpStatus.FORBIDDEN),

    // OAuth2 Errors (OAUTH_*)
    OAUTH_STATE_TOKEN_INVALID("OAUTH_001", "Invalid or expired state token", HttpStatus.UNAUTHORIZED),
    OAUTH_STATE_TOKEN_MISSING("OAUTH_002", "State token is required", HttpStatus.BAD_REQUEST),
    OAUTH_EMAIL_NOT_FOUND("OAUTH_003", "Email not found from OAuth2 provider", HttpStatus.BAD_REQUEST),
    OAUTH_PROVIDER_NOT_SUPPORTED("OAUTH_004", "OAuth2 provider not supported", HttpStatus.BAD_REQUEST),

    // User Errors (USER_*)
    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USER_002", "User already exists", HttpStatus.CONFLICT),
    USER_EMAIL_INVALID("USER_003", "Invalid email format", HttpStatus.BAD_REQUEST),
    USER_INVALID_STATUS("USER_004", "Invalid user status", HttpStatus.BAD_REQUEST),

    // OTP Errors (OTP_*)
    AUTH_OTP_INVALID("OTP_001", "Invalid OTP code", HttpStatus.BAD_REQUEST),
    AUTH_OTP_EXPIRED("OTP_002", "OTP has expired or was not found", HttpStatus.BAD_REQUEST),
    AUTH_OTP_EMAIL_NOT_PENDING("OTP_003", "No pending verification found for this email", HttpStatus.BAD_REQUEST),

    // User errors
    USER_PASSWORD_INVALID("USER_005", "Password must be at least 8 characters", HttpStatus.BAD_REQUEST),
    USER_EMAIL_ALREADY_EXISTS("USER_006", "An account with email ''{0}'' already exists", HttpStatus.CONFLICT),
    USER_USERNAME_ALREADY_EXISTS("USER_007", "Username ''{0}'' is already taken", HttpStatus.CONFLICT),
    USER_USERNAME_INVALID("USER_008", "Username must be 3–30 characters and may only contain lowercase letters, numbers, and underscores", HttpStatus.BAD_REQUEST),

    // Validation Errors (VALIDATION_*)
    VALIDATION_FAILED("VALIDATION_001", "Validation failed", HttpStatus.BAD_REQUEST),
    VALIDATION_FIELD_REQUIRED("VALIDATION_002", "Required field is missing", HttpStatus.BAD_REQUEST),
    VALIDATION_FIELD_INVALID("VALIDATION_003", "Invalid field value", HttpStatus.BAD_REQUEST),

    // Server Errors (SERVER_*)
    SERVER_INTERNAL_ERROR("SERVER_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVER_DATABASE_ERROR("SERVER_002", "Database error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVER_EXTERNAL_SERVICE_ERROR("SERVER_003", "External service error", HttpStatus.SERVICE_UNAVAILABLE),

    // Resource Errors (RESOURCE_*)
    RESOURCE_NOT_FOUND("RESOURCE_001", "Resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("RESOURCE_002", "Resource already exists", HttpStatus.CONFLICT),

    // Request Errors (REQUEST_*)
    REQUEST_BODY_INVALID("REQUEST_001", "Invalid request body", HttpStatus.BAD_REQUEST),
    REQUEST_PARAMETER_MISSING("REQUEST_002", "Required parameter is missing", HttpStatus.BAD_REQUEST),
    REQUEST_METHOD_NOT_ALLOWED("REQUEST_003", "HTTP method not allowed", HttpStatus.METHOD_NOT_ALLOWED),

    // Rate Limiting (RATE_*)
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests — please slow down and try again", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
