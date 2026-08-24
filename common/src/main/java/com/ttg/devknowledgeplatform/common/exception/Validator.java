package com.ttg.devknowledgeplatform.common.exception;

import java.util.Optional;

/**
 * Static guard-clause checks that collapse the repeated
 * {@code if (!condition) { throw new BusinessException(errorCode, args); }} shape into one call —
 * the same idiom as {@code org.springframework.util.Assert}/Guava's {@code Preconditions}, but
 * tied to this reactor's own {@link ErrorCode}-driven exceptions instead of a plain
 * {@code IllegalArgumentException}.
 *
 * <p><b>Deliberately named {@code Validator}, not {@code Assertions}/{@code Guard}</b>, even though
 * {@code jakarta.validation.Validator} (Bean Validation's own interface) is on every module's
 * classpath via {@code spring-boot-starter-validation} — the two are never imported in the same
 * file today, so the collision is accepted as a known trade-off rather than worked around; a future
 * file that genuinely needs both will have to fully-qualify one of them.
 *
 * <p>Every check here throws {@link BusinessException} — a strictly more specific type than the
 * plain {@link ApiException} many call sites threw directly before this class existed — except
 * {@link #notFound}, which throws the still-more-specific {@link ResourceNotFoundException}.
 * {@link GlobalExceptionHandler} handles the whole hierarchy identically (one
 * {@code @ExceptionHandler(ApiException.class)}), so adopting this class is a semantic
 * clarification at existing call sites, never a behavior change.
 */
public final class Validator {

    private Validator() {
    }

    /** Throws {@code errorCode}'s own zero-argument message if {@code condition} is false. */
    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }

    /** Throws with a call-site-built {@code message}, bypassing {@code errorCode}'s own template. */
    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    /** Throws {@code errorCode}'s own message template, substituting {@code args} positionally. */
    public static void isTrue(boolean condition, ErrorCode errorCode, Object... args) {
        if (!condition) {
            throw new BusinessException(errorCode, args);
        }
    }

    public static void isFalse(boolean condition, ErrorCode errorCode) {
        isTrue(!condition, errorCode);
    }

    public static void isFalse(boolean condition, ErrorCode errorCode, String message) {
        isTrue(!condition, errorCode, message);
    }

    public static void isFalse(boolean condition, ErrorCode errorCode, Object... args) {
        isTrue(!condition, errorCode, args);
    }

    /** Returns {@code value} unchanged when non-null; otherwise throws. */
    public static <T> T notNull(T value, ErrorCode errorCode, Object... args) {
        isTrue(value != null, errorCode, args);
        return value;
    }

    public static void isNull(Object value, ErrorCode errorCode, Object... args) {
        isTrue(value == null, errorCode, args);
    }

    /**
     * Unwraps {@code value} when present; otherwise throws {@link ResourceNotFoundException} —
     * the direct replacement for the repeated
     * {@code repo.findById(id).orElseThrow(() -> new ResourceNotFoundException(errorCode, id))}
     * shape found throughout every module's service layer.
     */
    public static <T> T notFound(Optional<T> value, ErrorCode errorCode, Object... args) {
        return value.orElseThrow(() -> new ResourceNotFoundException(errorCode, args));
    }
}
