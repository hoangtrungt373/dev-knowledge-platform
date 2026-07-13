package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.enums.GroupMemberRole;

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
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A user's membership in a {@link Group}, carrying their {@link GroupMemberRole}. One row per
 * (group, user) pair — enforced by {@code UK_GROUP_MEMBER_GROUP_USER}.
 */
@Entity
@Table(
        name = "GROUP_MEMBER",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "UK_GROUP_MEMBER_GROUP_USER", columnNames = {"GROUP_ID", "USER_ID"})
)
@AttributeOverride(name = "id", column = @Column(name = "GROUP_MEMBER_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"group", "user"})
@ToString(exclude = {"group", "user"})
public class GroupMember extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 20, nullable = false)
    private GroupMemberRole role;
}
