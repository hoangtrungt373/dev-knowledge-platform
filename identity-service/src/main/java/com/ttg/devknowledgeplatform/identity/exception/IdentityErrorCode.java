package com.ttg.devknowledgeplatform.identity.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code identity-service} — registration and Keycloak Admin API integration.
 *
 * Format: MODULE_ACTION_ERROR
 * Example: IDENTITY_001
 */
@Getter
public enum IdentityErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS("IDENTITY_001", "An account with email ''{0}'' already exists", HttpStatus.CONFLICT),
    KEYCLOAK_USER_CREATE_FAILED("IDENTITY_002", "Unable to create account at this time. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_ALREADY_VERIFIED("IDENTITY_003", "This email address is already verified", HttpStatus.CONFLICT),
    VERIFICATION_EMAIL_SEND_FAILED("IDENTITY_004", "Unable to send verification email at this time. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR),
    KEYCLOAK_USER_UPDATE_FAILED("IDENTITY_005", "Unable to update profile at this time. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    IdentityErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
