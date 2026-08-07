package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.social.enums.ProfileStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * This module's own lean projection of a Keycloak identity — every {@code @ManyToOne} in this
 * module's entity graph ({@code FriendRequest.requester}/{@code addressee},
 * {@code Friendship.user1}/{@code user2}, {@code UserBlock.blocker}/{@code blocked},
 * {@code GroupMember.user}, {@code DmThread.user1}/{@code user2}, {@code DmMessage.sender},
 * {@code ChannelMessage.sender}) points here, never at {@code common.entity.User}.
 *
 * <p>Deliberately NOT a full snapshot of that shared entity's column set — unlike {@code gateway}'s
 * {@code product.USER}/{@code identity-service}'s {@code identity.USER}, which reuse
 * {@code common.entity.User} directly. Every field here is one this module's code actually reads or
 * writes (verified by grepping real usages, not guessed): {@code profileUuid}/
 * {@code keycloakSubjectId}/{@code email} for JIT-provisioning lookup, {@code username}/
 * {@code firstName}/{@code lastName}/{@code profilePicture}/{@code status} for search and display,
 * {@code seedId} for the demo-data seeders. No {@code password}, OAuth {@code provider}, {@code role},
 * {@code emailVerified}, or {@code enabled} — this module has no auth-lifecycle concern, no
 * admin-gated endpoint, and nothing reads those fields anywhere in this module's code. See this
 * module's {@code CLAUDE.md} for the full reasoning behind avoiding a shared-entity coupling here.
 */
@Entity
@Table(name = "PROFILE", schema = "social")
@AttributeOverride(name = "id", column = @Column(name = "PROFILE_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SocialProfile extends AbstractEntity {

    @NotNull
    @Size(max = 36)
    @Column(name = "PROFILE_UUID", length = 36, nullable = false)
    private String profileUuid;

    @NotNull
    @Size(max = 255)
    @Column(name = "USERNAME", length = 255, nullable = false)
    private String username;

    @NotNull
    @Size(max = 255)
    @Column(name = "EMAIL", length = 255, nullable = false)
    private String email;

    @Size(max = 255)
    @Column(name = "FIRST_NAME", length = 255)
    private String firstName;

    @Size(max = 255)
    @Column(name = "LAST_NAME", length = 255)
    private String lastName;

    @Size(max = 500)
    @Column(name = "PROFILE_PICTURE", length = 500)
    private String profilePicture;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    @Builder.Default
    private ProfileStatus status = ProfileStatus.OFFLINE;

    @Size(max = 255)
    @Column(name = "KEYCLOAK_SUBJECT_ID", length = 255)
    private String keycloakSubjectId;

    @Size(max = 100)
    @Column(name = "SEED_ID", length = 100)
    private String seedId;
}
