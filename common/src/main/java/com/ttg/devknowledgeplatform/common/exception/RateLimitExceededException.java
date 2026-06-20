package com.ttg.devknowledgeplatform.common.exception;

/**
 * Thrown when an authenticated user exceeds the configured request rate limit
 * for the AI chat endpoint.
 *
 * <p>Handled by {@link GlobalExceptionHandler#handleRateLimit} which maps it to
 * {@code 429 Too Many Requests} with a {@code Retry-After: 60} response header.
 */
public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
