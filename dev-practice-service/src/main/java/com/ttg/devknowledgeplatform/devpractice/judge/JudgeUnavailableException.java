package com.ttg.devknowledgeplatform.devpractice.judge;

/**
 * The judge backend itself failed — unreachable, timed out, rate-limited past every retry, or
 * rejected the request (e.g. a bad API key) — as opposed to the user's code failing, which is a
 * normal {@link Judge0SubmissionResult}. Part of {@link JudgeClient}'s contract, so callers never
 * see the transport's own exception types (Spring's {@code RestClientException} hierarchy) — the
 * same boundary the Adapter already draws for statuses and language ids.
 *
 * <p>The message is operator-facing detail (HTTP status, endpoint) for logs; it must not be shown
 * to the submitting user as-is.
 */
public class JudgeUnavailableException extends RuntimeException {

    public JudgeUnavailableException(String message) {
        super(message);
    }

    public JudgeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
