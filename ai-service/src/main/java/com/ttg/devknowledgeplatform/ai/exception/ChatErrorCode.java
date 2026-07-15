package com.ttg.devknowledgeplatform.ai.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code ai-service}'s chat-session orchestration ({@code ChatSessionServiceImpl}).
 *
 * Format: MODULE_ACTION_ERROR
 * Example: CHAT_SESSION_NOT_FOUND
 */
@Getter
public enum ChatErrorCode implements ErrorCode {

    // Chat Session Errors (CHAT_*)
    CHAT_SESSION_NOT_FOUND("CHAT_001", "Chat session not found or does not belong to the current user", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ChatErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
