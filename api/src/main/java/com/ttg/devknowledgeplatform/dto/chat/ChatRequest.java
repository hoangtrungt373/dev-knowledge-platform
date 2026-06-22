package com.ttg.devknowledgeplatform.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming payload for {@code POST /api/v1/chat} and {@code POST /api/v1/chat/stream}.
 *
 * @param question  the user's natural-language question; must not be blank and is capped at 2 000 characters
 *                  to prevent excessively long prompts from being forwarded to the embedding model and LLM
 * @param sessionId optional ID of an existing conversation session; {@code null} creates a new session.
 *                  The session must belong to the authenticated user — a mismatched ID returns 404.
 */
public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must not exceed 2000 characters")
        String question,
        Integer sessionId
) {}
