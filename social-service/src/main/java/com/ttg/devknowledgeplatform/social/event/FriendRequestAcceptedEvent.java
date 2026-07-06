package com.ttg.devknowledgeplatform.social.event;

/**
 * Published by {@code FriendServiceImpl} right after a {@code Friendship} row is created —
 * either from an explicit accept, or from the auto-accept path when both users had already
 * requested each other.
 *
 * @param friendshipId primary key of the newly created {@code Friendship}
 * @param user1Id      lower of the two user IDs in the canonical pair
 * @param user2Id      higher of the two user IDs in the canonical pair
 */
public record FriendRequestAcceptedEvent(Integer friendshipId, Integer user1Id, Integer user2Id) {
}
