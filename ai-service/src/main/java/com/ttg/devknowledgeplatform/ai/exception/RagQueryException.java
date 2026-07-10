package com.ttg.devknowledgeplatform.ai.exception;

import com.ttg.devknowledgeplatform.common.exception.ApiException;

/**
 * Thrown when the RAG query pipeline fails due to an external service error —
 * typically an OpenAI API timeout, rate limit, or unexpected response.
 *
 * <p>Caught by {@link com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler}
 * via the {@code ApiException} handler, which maps it to
 * {@code 503 Service Unavailable} with the {@code AI_001} error code.
 *
 * <p>Transient failures (429, 5xx from OpenAI) are retried automatically by
 * LangChain4j before this exception is raised — so by the time it reaches the
 * handler, all retries have been exhausted.
 */
public class RagQueryException extends ApiException {

    public RagQueryException(String message, Throwable cause) {
        super(AiErrorCode.AI_SERVICE_UNAVAILABLE, message, cause);
    }
}
