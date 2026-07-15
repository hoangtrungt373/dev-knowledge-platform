package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.common.entity.ChatSession;
import com.ttg.devknowledgeplatform.ai.dto.chat.ChatSessionSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link ChatSession}.
 *
 * <p>The {@code findByIdAndUserId} query enforces ownership: a user can only access
 * their own sessions, preventing session ID enumeration attacks.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Integer> {

    /**
     * Finds a session by its ID and owning user, returning empty if either the session
     * does not exist or belongs to a different user.
     *
     * @param id     the session primary key
     * @param userId the authenticated user's identifier
     * @return the session if found and owned by the user
     */
    Optional<ChatSession> findByIdAndUserId(Integer id, Integer userId);

    /**
     * Returns a summary row for every session belonging to {@code userId}, ordered by most
     * recent activity first. Message count is computed in a single query via {@code COUNT(m)}
     * to avoid N+1 fetches.
     *
     * @param userId the authenticated user's identifier
     * @return list of session summaries, newest first
     */
    @Query("""
            SELECT new com.ttg.devknowledgeplatform.ai.dto.chat.ChatSessionSummaryDto(
                s.id, s.title, s.lastActivityAt, COUNT(m)
            )
            FROM ChatSession s LEFT JOIN s.messages m
            WHERE s.userId = :userId
            GROUP BY s.id, s.title, s.lastActivityAt
            ORDER BY s.lastActivityAt DESC
            """)
    List<ChatSessionSummaryDto> findSessionSummariesByUserId(@Param("userId") Integer userId);
}
