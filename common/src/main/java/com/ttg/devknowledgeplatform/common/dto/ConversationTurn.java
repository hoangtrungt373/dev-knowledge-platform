package com.ttg.devknowledgeplatform.common.dto;

import com.ttg.devknowledgeplatform.common.enums.ChatMessageRole;

/**
 * Represents a single turn in a multi-turn conversation — either a user question or an AI answer.
 *
 * <p>Used to pass conversation history from the {@code api} layer down to the {@code ai-service}
 * without leaking LangChain4j types across module boundaries. The {@code ai-service} converts
 * these records into LangChain4j {@code UserMessage}/{@code AiMessage} objects internally.
 *
 * @param role    {@link ChatMessageRole#USER} for a user question,
 *                {@link ChatMessageRole#ASSISTANT} for an AI answer
 * @param content the full text of this message turn
 */
public record ConversationTurn(ChatMessageRole role, String content) {}
