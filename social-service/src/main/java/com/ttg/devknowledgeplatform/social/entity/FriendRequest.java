package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A friend request from {@link #requester} to {@link #addressee}, tracked through its
 * {@link FriendRequestStatus} lifecycle. Once accepted, the friendship itself is represented
 * by a separate {@link Friendship} row — this entity only ever reflects the request that led
 * to it (or its rejection/cancellation).
 */
@Entity
@Table(name = "FRIEND_REQUEST", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "FRIEND_REQUEST_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"requester", "addressee"})
@ToString(exclude = {"requester", "addressee"})
public class FriendRequest extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUESTER_ID", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ADDRESSEE_ID", nullable = false)
    private User addressee;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    private FriendRequestStatus status;
}
