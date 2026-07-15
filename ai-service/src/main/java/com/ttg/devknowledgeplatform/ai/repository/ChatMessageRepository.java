package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence operations for {@link ChatMessage}.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    /**
     * Returns all messages for a session in chronological order (oldest first).
     * Used for the history GET endpoint where the full conversation is displayed.
     *
     * @param sessionId the owning session's primary key
     * @return messages ordered by turn index ascending
     */
    List<ChatMessage> findByChatSession_IdOrderByTurnIndexAsc(Integer sessionId);

    /**
     * Returns the most recent messages for a session in reverse chronological order.
     * Pass a {@link Pageable} to cap the result (e.g. {@code PageRequest.of(0, 10)} for last 10).
     * Callers must reverse the list before using it as LLM context.
     *
     * @param sessionId the owning session's primary key
     * @param pageable  limits the result count
     * @return messages ordered by turn index descending, up to the pageable limit
     */
    List<ChatMessage> findByChatSession_IdOrderByTurnIndexDesc(Integer sessionId, Pageable pageable);

    /**
     * Returns the highest turn index currently stored for a session, or {@code -1} if the session
     * has no messages yet. Adding 1 to the result gives the next available index.
     *
     * @param sessionId the owning session's primary key
     * @return max turn index, or -1 if no messages exist
     */
    @Query("SELECT COALESCE(MAX(m.turnIndex), -1) FROM ChatMessage m WHERE m.chatSession.id = :sessionId")
    int findMaxTurnIndexBySessionId(@Param("sessionId") Integer sessionId);
}
