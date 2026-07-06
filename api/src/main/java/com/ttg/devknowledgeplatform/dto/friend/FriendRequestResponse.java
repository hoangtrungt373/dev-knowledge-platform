package com.ttg.devknowledgeplatform.dto.friend;

import java.time.Instant;

/**
 * A friend request, in either the incoming or outgoing direction.
 *
 * @param id        primary key, used to accept/reject/cancel
 * @param requester the user who sent the request
 * @param addressee the user who received the request
 * @param status    current lifecycle status (e.g. {@code PENDING}, {@code ACCEPTED})
 * @param createdAt when the request was sent
 */
public record FriendRequestResponse(
        Integer id,
        UserSummaryResponse requester,
        UserSummaryResponse addressee,
        String status,
        Instant createdAt
) {
}
