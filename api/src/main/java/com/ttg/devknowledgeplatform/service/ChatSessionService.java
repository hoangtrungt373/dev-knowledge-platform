package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import com.ttg.devknowledgeplatform.common.entity.ChatMessage;
import com.ttg.devknowledgeplatform.dto.chat.ChatSessionSummaryDto;

import java.util.List;

/**
 * Manages conversation sessions for the RAG chatbot.
 *
 * <p>A session is a persistent container for a user's chat history. Each Q&A exchange
 * is stored as a pair of {@link ChatMessage} rows (USER + ASSISTANT). The last
 * {@code N} pairs are retrieved and injected into the LLM as prior context on each request.
 *
 * <p>Sessions expire after 24 hours of inactivity. An expired session is cleared rather
 * than deleted — the ID remains valid but the history is reset, giving the user a fresh
 * context while preserving the session reference they may have stored client-side.
 */
public interface ChatSessionService {

    /**
     * Returns the ID of an existing session or creates a new one.
     *
     * <p>If {@code requestedSessionId} is {@code null}, a new session is created.
     * If it is provided, the session is loaded and verified to belong to {@code userId}
     * (throws {@link com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException}
     * otherwise). If the session has been inactive for more than 24 hours, its message
     * history is cleared before returning.
     *
     * @param requestedSessionId optional session ID from the client request; null = new session
     * @param userId             the authenticated user's identifier
     * @return the active session ID to return to the client
     */
    Integer getOrCreateSessionId(Integer requestedSessionId, Integer userId);

    /**
     * Returns the last {@code maxTurns} Q&A pairs from a session as conversation turns,
     * ordered chronologically (oldest first) so they can be prepended to the LLM message list.
     *
     * @param sessionId the session ID returned by {@link #getOrCreateSessionId}
     * @param maxTurns  maximum number of Q&A pairs to include (2 messages each)
     * @return ordered list of conversation turns, oldest first
     */
    List<ConversationTurn> getRecentTurns(Integer sessionId, int maxTurns);

    /**
     * Saves a completed Q&A exchange to the session and updates {@code lastActivityAt}.
     *
     * <p>Two rows are written: a USER message followed by an ASSISTANT message at consecutive
     * turn indices. Safe to call from a background thread (e.g. SSE streaming callbacks).
     *
     * @param sessionId the session ID
     * @param question  the user's original question
     * @param answer    the LLM-generated answer
     */
    void addTurn(Integer sessionId, String question, String answer);

    /**
     * Returns a summary of every session belonging to {@code userId}, ordered by most recent
     * activity first. Suitable for rendering a conversation list (sidebar).
     *
     * @param userId the authenticated user's identifier
     * @return list of session summaries, newest first
     */
    List<ChatSessionSummaryDto> listSessions(Integer userId);

    /**
     * Returns the full message history for a session, verifying that it belongs to {@code userId}.
     *
     * @param sessionId the session ID
     * @param userId    the authenticated user's identifier
     * @return all messages ordered by turn index ascending
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException
     *         if the session does not exist or belongs to a different user
     */
    List<ChatMessage> getHistory(Integer sessionId, Integer userId);
}
