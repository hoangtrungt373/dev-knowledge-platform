package com.ttg.devknowledgeplatform.social.event;

/**
 * Published by {@code FriendServiceImpl.sendRequest} right after a new pending
 * {@code FriendRequest} is persisted. A record is used because it's immutable, which prevents
 * a listener from mutating shared state on a different thread — same reasoning as
 * {@code ai-service}'s {@code PipelineCompletedEvent}.
 *
 * @param requestId   primary key of the newly created {@code FriendRequest}
 * @param requesterId ID of the user who sent the request
 * @param addresseeId ID of the user who received it
 */
public record FriendRequestSentEvent(Integer requestId, Integer requesterId, Integer addresseeId) {
}
