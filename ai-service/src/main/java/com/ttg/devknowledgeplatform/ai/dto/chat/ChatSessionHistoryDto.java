package com.ttg.devknowledgeplatform.ai.dto.chat;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/chat/sessions/{id}}.
 *
 * @param sessionId the session's primary key
 * @param messages  all messages in the session ordered by turn index ascending
 */
public record ChatSessionHistoryDto(
        Integer sessionId,
        List<MessageDto> messages
) {

    /**
     * A single message within the session history.
     *
     * @param role       {@code "USER"} or {@code "ASSISTANT"}
     * @param content    the full message text
     * @param turnIndex  0-based ordering position within the session
     */
    public record MessageDto(String role, String content, int turnIndex) {}
}
