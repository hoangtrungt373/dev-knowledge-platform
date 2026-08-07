package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.ChatSession;
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
     * @param id       the session primary key
     * @param userUuid the authenticated user's Keycloak subject id
     * @return the session if found and owned by the user
     */
    Optional<ChatSession> findByIdAndUserUuid(Integer id, String userUuid);

    /**
     * Returns a summary row for every session belonging to {@code userUuid}, ordered by most
     * recent activity first. Message count is computed in a single query via {@code COUNT(m)}
     * to avoid N+1 fetches.
     *
     * @param userUuid the authenticated user's Keycloak subject id
     * @return list of session summaries, newest first
     */
    @Query("""
            SELECT new com.ttg.devknowledgeplatform.ai.dto.chat.ChatSessionSummaryDto(
                s.id, s.title, s.lastActivityAt, COUNT(m)
            )
            FROM ChatSession s LEFT JOIN s.messages m
            WHERE s.userUuid = :userUuid
            GROUP BY s.id, s.title, s.lastActivityAt
            ORDER BY s.lastActivityAt DESC
            """)
    List<ChatSessionSummaryDto> findSessionSummariesByUserUuid(@Param("userUuid") String userUuid);
}
