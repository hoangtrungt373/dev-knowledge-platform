package com.ttg.devknowledgeplatform.common.exception;

import java.text.MessageFormat;

import org.springframework.http.HttpStatus;

/**
 * Contract for a domain error code, implemented by one enum per module that owns errors
 * (e.g. {@link CommonErrorCode} here, {@code ContentErrorCode} in {@code content-service}).
 *
 * <p>Splitting this out as an interface — rather than one shared enum listing every module's
 * codes — lets {@link ApiException}/{@link BusinessException}/{@link GlobalExceptionHandler}
 * stay module-agnostic while each feature module owns and evolves its own error codes
 * independently, without a compile-time dependency back onto that module from {@code common}.
 */
public interface ErrorCode {

    String getCode();

    String getMessage();

    HttpStatus getHttpStatus();

    /**
     * Renders {@link #getMessage()} as a {@link MessageFormat} pattern, substituting
     * {@code {0}}, {@code {1}}, ... with {@code args}. Call with no arguments to resolve a plain
     * message (still runs it through {@code MessageFormat} so {@code ''} escaping is handled
     * consistently whether or not the call site supplies arguments).
     *
     * <p>An enum constant whose message embeds a placeholder must double any literal single quote
     * that surrounds it, e.g. {@code "A category with name ''{0}'' already exists"} —
     * {@code MessageFormat} otherwise treats a lone {@code '} as the start of a quoted (i.e.
     * literal, non-substituting) span and silently swallows the placeholder.
     *
     * @param args positional values to substitute into the message template
     * @return the formatted message
     */
    default String formatMessage(Object... args) {
        return MessageFormat.format(getMessage(), args);
    }
}
