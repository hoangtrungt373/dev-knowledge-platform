package com.ttg.devknowledgeplatform.common.exception;

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
}
