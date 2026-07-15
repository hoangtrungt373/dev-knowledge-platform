package com.ttg.devknowledgeplatform.ai.dto.chat;

import java.time.Instant;

/**
 * Summary entry for {@code GET /api/v1/chat/sessions} — one row per conversation session.
 *
 * <p>Used to render a session list (e.g. a sidebar like ChatGPT's conversation history).
 * Does not include message content; use {@code GET /api/v1/chat/sessions/{id}} for the full history.
 *
 * @param sessionId        the session's primary key; pass back as {@code sessionId} in the next request
 *                         to resume this conversation
 * @param title            auto-generated from the first question (up to 100 chars); {@code null}
 *                         if the session has no messages yet
 * @param lastActivityAt   timestamp of the most recent Q&A exchange; use for "Today / Yesterday" grouping
 * @param messageCount     total number of messages (USER + ASSISTANT) stored in the session
 */
public record ChatSessionSummaryDto(
        Integer sessionId,
        String title,
        Instant lastActivityAt,
        Long messageCount
) {}
