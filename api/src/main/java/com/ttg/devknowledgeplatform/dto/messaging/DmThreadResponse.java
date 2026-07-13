package com.ttg.devknowledgeplatform.dto.messaging;

import java.time.Instant;

import com.ttg.devknowledgeplatform.dto.friend.UserSummaryResponse;

/**
 * One entry in the authenticated user's list of DM conversations.
 *
 * @param id            primary key
 * @param otherUser     public profile summary of the other participant
 * @param lastMessageAt when the most recent message in this thread was sent; {@code null} if the
 *                      thread was just created and has no messages yet
 */
public record DmThreadResponse(Integer id, UserSummaryResponse otherUser, Instant lastMessageAt) {
}
