package com.ttg.devknowledgeplatform.social.dto.messaging;

/**
 * Error payload sent to a STOMP client's private {@code /user/queue/errors} destination when a
 * {@code @MessageMapping} handler rejects a message — the WebSocket-side equivalent of the JSON
 * error body {@code GlobalExceptionHandler} returns for REST.
 */
public record WsErrorResponse(String errorCode, String message) {
}
