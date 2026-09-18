package com.ttg.devknowledgeplatform.devpractice.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Domain error codes owned by {@code dev-practice-service}, grouped by sub-resource prefix
 * ({@code PROBLEM_00x}, {@code SUBMISSION_00x}) — mirrors {@code task-service}'s
 * {@code TaskErrorCode}/{@code content-service}'s {@code ContentErrorCode}.
 */
@Getter
public enum DevPracticeErrorCode implements ErrorCode {

    // Problem errors (PROBLEM_*)
    PROBLEM_NOT_FOUND("PROBLEM_001", "Problem not found", HttpStatus.NOT_FOUND),
    PROBLEM_SLUG_CONFLICT("PROBLEM_002", "Unable to generate a unique slug for this problem", HttpStatus.CONFLICT),

    // Submission errors (SUBMISSION_*)
    SUBMISSION_NOT_FOUND("SUBMISSION_001", "Submission not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    DevPracticeErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
