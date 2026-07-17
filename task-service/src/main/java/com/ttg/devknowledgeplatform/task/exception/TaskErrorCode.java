package com.ttg.devknowledgeplatform.task.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code task-service} — projects and tasks. One enum per module, same
 * pattern as {@code social-service}'s {@code SocialErrorCode}.
 *
 * <p>No separate "forbidden"/"not owned" code: a project or task that exists but belongs to a
 * different owner reuses the same {@code *_NOT_FOUND} code as a genuinely missing id, rather than
 * a distinguishable 403 — avoids leaking "this id exists but isn't yours" to the caller, mirroring
 * {@code social-service}'s mutual-invisibility handling of blocked users.
 */
@Getter
public enum TaskErrorCode implements ErrorCode {

    // Project Errors (PROJECT_*)
    PROJECT_NOT_FOUND("PROJECT_001", "Project not found", HttpStatus.NOT_FOUND),

    // Task Errors (TASK_*)
    TASK_NOT_FOUND("TASK_001", "Task not found", HttpStatus.NOT_FOUND),
    TASK_CONTENT_ITEM_NOT_FOUND("TASK_002", "Referenced content item not found", HttpStatus.NOT_FOUND),
    TASK_INVALID_STATUS_TRANSITION("TASK_003", "Task is already in this status", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    TaskErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
