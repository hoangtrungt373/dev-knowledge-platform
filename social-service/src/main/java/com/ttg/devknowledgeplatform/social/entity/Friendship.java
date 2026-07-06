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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * An accepted friendship between two users, stored once per pair with {@link #user1} always
 * the lower user ID and {@link #user2} the higher — see the {@code CKC_FRIENDSHIP_ORDER} check
 * constraint in the schema. Callers must canonicalize the pair before constructing this entity;
 * {@link com.ttg.devknowledgeplatform.social.service.impl.FriendServiceImpl} does this.
 */
@Entity
@Table(name = "FRIENDSHIP", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "FRIENDSHIP_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"user1", "user2"})
@ToString(exclude = {"user1", "user2"})
public class Friendship extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID_1", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID_2", nullable = false)
    private User user2;
}
