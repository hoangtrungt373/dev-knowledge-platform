package com.ttg.devknowledgeplatform.social.dto.friend;

import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;

/**
 * One row of a user search or a public profile lookup — carries the viewer's relationship to
 * the returned user so the GUI knows which action button (add/cancel/accept/unfriend) to show.
 *
 * @param user               public profile summary
 * @param relationshipStatus the viewer's relationship to {@code user}
 * @param mutualFriendCount  number of friends the viewer and {@code user} have in common
 */
public record UserSearchResultResponse(
        UserSummaryResponse user,
        RelationshipStatus relationshipStatus,
        long mutualFriendCount
) {
}
