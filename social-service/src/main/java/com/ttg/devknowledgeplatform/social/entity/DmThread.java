package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * A 1:1 direct-message thread between two users, created lazily on the first message sent between
 * them (see {@code DmMessage}). Stored once per pair with {@link #user1} always the lower user ID
 * and {@link #user2} the higher — same canonicalization convention as {@link Friendship}'s
 * {@code CKC_FRIENDSHIP_ORDER}; callers must canonicalize the pair before constructing this entity.
 *
 * <p>Opening or sending to a thread requires an accepted {@link Friendship} between the two users;
 * that check happens in the service layer, not here.
 */
@Entity
@Table(
        name = "DM_THREAD",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "UK_DM_THREAD_USER_PAIR", columnNames = {"USER_ID_1", "USER_ID_2"})
)
@AttributeOverride(name = "id", column = @Column(name = "DM_THREAD_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"user1", "user2"})
@ToString(exclude = {"user1", "user2"})
public class DmThread extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID_1", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID_2", nullable = false)
    private User user2;

    /**
     * Timestamp of the most recently sent {@code DmMessage} in this thread. Denormalized so the
     * "my DM conversations, sorted by recent activity" list doesn't need a {@code MAX(dteCreation)}
     * aggregate over messages — same reasoning as {@code ChatSession.lastActivityAt}.
     */
    @Column(name = "LAST_MESSAGE_AT")
    private Instant lastMessageAt;
}
