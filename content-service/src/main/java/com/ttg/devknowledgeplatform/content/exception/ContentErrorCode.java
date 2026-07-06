package com.ttg.devknowledgeplatform.content.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code content-service} — categories, tags, and content items
 * (question-and-answer, article).
 *
 * Format: MODULE_ACTION_ERROR
 * Example: CATEGORY_NOT_FOUND, TAG_IN_USE
 */
@Getter
public enum ContentErrorCode implements ErrorCode {

    // Category Errors (CATEGORY_*)
    CATEGORY_NOT_FOUND("CATEGORY_001", "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_NAME_CONFLICT("CATEGORY_002", "A category with this name already exists", HttpStatus.CONFLICT),
    CATEGORY_SLUG_CONFLICT("CATEGORY_003", "Unable to generate unique slug for category", HttpStatus.CONFLICT),
    CATEGORY_CYCLIC_PARENT("CATEGORY_004", "Invalid parent: would create a cycle in the category tree", HttpStatus.BAD_REQUEST),
    CATEGORY_LIST_FILTER_CONFLICT("CATEGORY_005", "Use only one of rootOnly or parentId", HttpStatus.BAD_REQUEST),
    CATEGORY_HAS_CHILDREN("CATEGORY_006", "Category has children and cannot be deleted", HttpStatus.CONFLICT),
    CATEGORY_IN_USE("CATEGORY_007", "Category is used by content items and cannot be deleted", HttpStatus.CONFLICT),

    // Tag Errors (TAG_*)
    TAG_NOT_FOUND("TAG_001", "Tag not found", HttpStatus.NOT_FOUND),
    TAG_NAME_CONFLICT("TAG_002", "A tag with this name already exists", HttpStatus.CONFLICT),
    TAG_SLUG_CONFLICT("TAG_003", "Unable to generate unique slug for tag", HttpStatus.CONFLICT),
    TAG_IN_USE("TAG_004", "Tag is used by content items and cannot be deleted", HttpStatus.CONFLICT),

    // Question & Answer Errors (QA_*)
    QUESTION_ANSWER_NOT_FOUND("QA_001", "Question not found", HttpStatus.NOT_FOUND),
    QUESTION_ANSWER_SLUG_CONFLICT("QA_002", "Slug conflict: unable to generate unique slug", HttpStatus.CONFLICT),
    QUESTION_ANSWER_IN_USE("QA_003", "Question is referenced and cannot be deleted", HttpStatus.CONFLICT),

    // Article Errors (ARTICLE_*)
    ARTICLE_NOT_FOUND("ARTICLE_001", "Article not found", HttpStatus.NOT_FOUND),
    ARTICLE_SLUG_CONFLICT("ARTICLE_002", "Slug conflict: unable to generate unique slug", HttpStatus.CONFLICT),
    ARTICLE_TYPE_INVALID("ARTICLE_003", "Article type must be ARTICLE or BLOG_POST", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ContentErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
