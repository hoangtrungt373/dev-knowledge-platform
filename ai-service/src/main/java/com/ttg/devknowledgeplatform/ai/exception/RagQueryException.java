package com.ttg.devknowledgeplatform.ai.exception;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

public class RagQueryException extends ApiException {

    public RagQueryException(String message, Throwable cause) {
        super(ErrorCode.AI_SERVICE_UNAVAILABLE, message, cause);
    }
}
