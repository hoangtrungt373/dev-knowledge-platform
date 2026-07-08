package com.ttg.devknowledgeplatform.common.entity;

import com.ttg.devknowledgeplatform.common.enums.ChatMessageRole;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A single message within a {@link ChatSession}, representing one turn by either the user or the AI.
 *
 * <p>Messages are ordered within their session by {@code turnIndex}, a 0-based counter that
 * increments by 1 per message. Each Q&A exchange adds two consecutive rows: one USER message
 * at index N and one ASSISTANT message at index N+1.
 */
@Entity
@Table(
        name = "CHAT_MESSAGE",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "UK_CHAT_MESSAGE_TURN_ORDER", columnNames = {"CHAT_SESSION_ID", "TURN_INDEX"})
)
@AttributeOverride(name = "id", column = @Column(name = "CHAT_MESSAGE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "chatSession")
@ToString(exclude = "chatSession")
public class ChatMessage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CHAT_SESSION_ID", nullable = false)
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 20, nullable = false)
    private ChatMessageRole role;

    /** Full text of the message — may be lengthy for AI answers. */
    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 0-based ordering index within the session; monotonically increasing. */
    @Column(name = "TURN_INDEX", nullable = false)
    private Integer turnIndex;
}
