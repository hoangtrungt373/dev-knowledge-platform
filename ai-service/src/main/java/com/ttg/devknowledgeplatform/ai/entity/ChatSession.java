package com.ttg.devknowledgeplatform.ai.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single conversation session between a user and the RAG chatbot.
 *
 * <p>Sessions are keyed by {@code userId} and carry a {@code lastActivityAt} timestamp
 * used to enforce a 24-hour inactivity TTL. When a session expires, its message history
 * is cleared and a fresh conversation begins under the same session ID.
 *
 * <p>Messages are stored as child {@link ChatMessage} rows ordered by {@code turnIndex}.
 *
 * <p>Once a session exceeds the summarisation threshold, older turns are compressed by
 * {@code ConversationSummarisationService} and stored in {@link #summary}. The RAG pipeline
 * injects the summary before the recent verbatim turns, keeping full history available
 * without inflating the LLM token budget.
 */
@Entity
@Table(name = "CHAT_SESSION", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "CHAT_SESSION_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "messages")
@ToString(exclude = "messages")
public class ChatSession extends AbstractEntity {

    /** Surrogate PK of the owning {@code User} row — integer FK without a JPA association to avoid eager loading. */
    @Column(name = "USER_ID", nullable = false)
    private Integer userId;

    /**
     * Auto-generated title derived from the first question in the session (capped at 100 chars).
     * Null until the first Q&A exchange is saved.
     */
    @Column(name = "TITLE", length = 500)
    private String title;

    /** Timestamp of the last message exchange; used to compute session expiry. */
    @Column(name = "LAST_ACTIVITY_AT", nullable = false)
    private Instant lastActivityAt;

    /**
     * LLM-generated rolling summary of turns older than the recent verbatim window.
     *
     * <p>Null until the session exceeds the summarisation threshold. Updated periodically
     * so the RAG pipeline can inject compact older context without overflowing the LLM
     * token budget.
     */
    @Column(name = "SUMMARY", columnDefinition = "TEXT")
    private String summary;

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("turnIndex ASC")
    private List<ChatMessage> messages = new ArrayList<>();
}
