package com.ttg.devknowledgeplatform.ai.dto;

import com.ttg.devknowledgeplatform.ai.enums.ChatMessageRole;

/**
 * Represents a single turn in a multi-turn conversation — either a user question or an AI answer.
 *
 * @param role    {@link ChatMessageRole#USER} for a user question,
 *                {@link ChatMessageRole#ASSISTANT} for an AI answer
 * @param content the full text of this message turn
 */
public record ConversationTurn(ChatMessageRole role, String content) {}
