package com.ttg.devknowledgeplatform.social.dto.friend;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

/**
 * Full public-profile view of a user, returned by {@code UserApi.getPublicProfile}.
 *
 * <p>Deliberately duplicated from {@code identity-service}'s own {@code UserInfoResponse} (used
 * there by {@code AuthApi.getCurrentUser}), not shared — {@code identity-service} is being
 * extracted into a standalone service (see the {@code project-microservices-extraction-plan}
 * memory / root {@code CLAUDE.md}), so this module can no longer depend on its DTOs. Unlike
 * {@link UserSummaryResponse}, this carries the full profile plus the viewer-relative
 * {@code relationshipStatus}/{@code mutualFriendCount} fields this module's own {@code FriendService}
 * adds.
 */
@Data
@Builder
public class UserInfoResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String status;
    private Instant createdAt;
    private Instant lastModified;

    /** Viewer's relationship to this user (e.g. {@code FRIENDS}, {@code STRANGER}); {@code null} when viewing anonymously or viewing your own profile. */
    private String relationshipStatus;

    /** Friends in common with the viewer; {@code null} under the same conditions as {@link #relationshipStatus}. */
    private Long mutualFriendCount;
}
