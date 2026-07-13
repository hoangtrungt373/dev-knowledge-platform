package com.ttg.devknowledgeplatform.dto.messaging;

import java.time.Instant;

import com.ttg.devknowledgeplatform.dto.friend.UserSummaryResponse;

/**
 * A user's membership in a group.
 *
 * @param user      public profile summary of the member
 * @param role      {@code OWNER}, {@code ADMIN}, or {@code MEMBER}
 * @param joinedAt  when this membership was created
 */
public record GroupMemberResponse(UserSummaryResponse user, String role, Instant joinedAt) {
}
