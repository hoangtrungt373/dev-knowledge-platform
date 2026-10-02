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
    PROBLEM_SIGNATURE_LOCKED("PROBLEM_003",
            "Cannot change a published problem's method name, return type, or parameters — move it back to DRAFT first",
            HttpStatus.CONFLICT),
    PROBLEM_TEST_CASE_ARITY_MISMATCH("PROBLEM_004",
            "Test case input {0} must be a JSON array with exactly {1} value(s), one per parameter",
            HttpStatus.BAD_REQUEST),
    PROBLEM_INVALID_IDENTIFIER("PROBLEM_005",
            "''{0}'' can''t be used as a {1} name: {2}", HttpStatus.BAD_REQUEST),
    PROBLEM_DUPLICATE_PARAMETER_NAME("PROBLEM_006",
            "Parameter name ''{0}'' is used more than once", HttpStatus.BAD_REQUEST),
    PROBLEM_HAS_SUBMISSIONS("PROBLEM_007",
            "This problem already has {0} submission(s) and can''t be deleted — archive it instead",
            HttpStatus.CONFLICT),
    PROBLEM_TEMPLATE_INVALID("PROBLEM_008", "Couldn''t read the code template: {0}", HttpStatus.BAD_REQUEST),
    PROBLEM_NOT_VERIFIED("PROBLEM_009",
            "This problem can''t be published yet: it has no accepted reference solution for its current "
                    + "signature and test cases. Save it as Draft, run a reference solution, then publish",
            HttpStatus.CONFLICT),

    // Problem tag errors (PROBLEM_TAG_*)
    PROBLEM_TAG_NOT_FOUND("PROBLEM_TAG_001", "Problem tag not found", HttpStatus.NOT_FOUND),
    PROBLEM_TAG_NAME_CONFLICT("PROBLEM_TAG_002", "A tag named ''{0}'' already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_SLUG_CONFLICT("PROBLEM_TAG_003", "Unable to generate a unique slug for this tag", HttpStatus.CONFLICT),
    PROBLEM_TAG_IN_USE("PROBLEM_TAG_004",
            "This tag is used by {0} problem(s) — remove it from them before deleting it", HttpStatus.CONFLICT),

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
