package com.ttg.devknowledgeplatform.dto.friend;

import java.time.Instant;

/**
 * One entry in the authenticated user's friend list.
 *
 * @param user         public profile summary of the friend
 * @param friendsSince when the friendship was established
 */
public record FriendSummaryResponse(
        UserSummaryResponse user,
        Instant friendsSince
) {
}
