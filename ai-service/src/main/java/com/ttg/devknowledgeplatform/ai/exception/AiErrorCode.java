package com.ttg.devknowledgeplatform.ai.exception;

import org.springframework.http.HttpStatus;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

import lombok.Getter;

/**
 * Error codes owned by {@code ai-service} — the RAG pipeline: embedding, retrieval, and
 * chat-model resolution.
 *
 * Format: MODULE_ACTION_ERROR
 * Example: AI_SERVICE_UNAVAILABLE, AI_MODEL_UNSUPPORTED
 */
@Getter
public enum AiErrorCode implements ErrorCode {

    // AI / RAG Errors (AI_*)
    AI_SERVICE_UNAVAILABLE("AI_001", "AI service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    AI_EMBEDDING_FAILED("AI_002", "Failed to generate embedding for the provided text", HttpStatus.SERVICE_UNAVAILABLE),
    AI_MODEL_UNSUPPORTED("AI_003", "Requested chat model ''{0}'' is not supported", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    AiErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
