package com.ttg.devknowledgeplatform.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;

/**
 * Owns the friend graph: search visibility, friend requests, friendships, and blocking.
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code FriendMapper} does the
 * entity-to-response mapping, matching how {@code ai-service}'s {@code RagQueryService} returns
 * internal models that {@code api} maps to {@code ChatResponse}.
 */
public interface FriendService {

    /**
     * Sends a friend request. If {@code addresseeUuid} already has a pending request to
     * {@code requesterId}, that mutual interest is treated as immediate confirmation: the
     * existing request is accepted and a {@link com.ttg.devknowledgeplatform.social.entity.Friendship}
     * is created instead of leaving two pending requests outstanding.
     *
     * @param requesterId   surrogate ID of the user sending the request
     * @param addresseeUuid public UUID of the user to friend
     * @return the resulting {@link FriendRequest} — {@code PENDING} for a fresh request, or the
     *         reverse request now marked {@code ACCEPTED} on the auto-accept path
     */
    FriendRequest sendRequest(Integer requesterId, String addresseeUuid);

    /**
     * Accepts a pending request addressed to {@code actingUserId}, creating a
     * {@link com.ttg.devknowledgeplatform.social.entity.Friendship}.
     */
    FriendRequest acceptRequest(Integer requestId, Integer actingUserId);

    /** Rejects a pending request addressed to {@code actingUserId}. */
    FriendRequest rejectRequest(Integer requestId, Integer actingUserId);

    /** Cancels a pending request originally sent by {@code actingUserId}. */
    FriendRequest cancelRequest(Integer requestId, Integer actingUserId);

    /** Removes an existing friendship between the two users. */
    void unfriend(Integer userId, String otherUserUuid);

    /**
     * Blocks a user: cascades by removing any existing friendship and cancelling any pending
     * request between the pair (either direction) before recording the block.
     */
    UserBlock block(Integer blockerId, String blockedUuid);

    /** Removes a block previously created by {@code blockerId}. Idempotent. */
    void unblock(Integer blockerId, String blockedUuid);

    /**
     * Resolves the relationship of {@code targetUuid} as seen by {@code viewerId}.
     *
     * <p>If {@code targetUuid} has blocked the viewer, this throws the same
     * {@code USER_NOT_FOUND} error used for a nonexistent user rather than returning
     * {@link RelationshipStatus#BLOCKED} — preserving mutual invisibility.
     */
    RelationshipStatus getRelationshipStatus(Integer viewerId, String targetUuid);

    /** Number of friends {@code viewerId} and {@code targetUuid} have in common. */
    long countMutualFriends(Integer viewerId, String targetUuid);

    /**
     * Friendship rows involving {@code userId}. Returned as {@link Friendship} rather than the
     * resolved "other user" so callers (the mapper) can also surface {@code friendsSince}.
     */
    Page<Friendship> listFriends(Integer userId, Pageable pageable);

    /** Pending requests addressed to {@code userId}. */
    Page<FriendRequest> listIncomingRequests(Integer userId, Pageable pageable);

    /** Pending requests sent by {@code userId}. */
    Page<FriendRequest> listOutgoingRequests(Integer userId, Pageable pageable);

    /** Users blocked by {@code blockerId}. */
    Page<User> listBlockedUsers(Integer blockerId, Pageable pageable);

    /**
     * Searches users visible to {@code viewerId} — excludes the viewer themselves and anyone
     * blocked in either direction. See {@code UserSpecification} for the exact/fuzzy match rules.
     */
    Page<User> searchUsers(Integer viewerId, String q, Pageable pageable);
}
