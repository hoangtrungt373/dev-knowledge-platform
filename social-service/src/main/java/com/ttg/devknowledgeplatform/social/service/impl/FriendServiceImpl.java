package com.ttg.devknowledgeplatform.social.service.impl;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.entity.SocialProfile;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;
import com.ttg.devknowledgeplatform.social.event.FriendRequestAcceptedEvent;
import com.ttg.devknowledgeplatform.social.event.FriendRequestSentEvent;
import com.ttg.devknowledgeplatform.social.exception.SocialErrorCode;
import com.ttg.devknowledgeplatform.social.repository.FriendRequestRepository;
import com.ttg.devknowledgeplatform.social.repository.FriendshipRepository;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;
import com.ttg.devknowledgeplatform.social.repository.UserBlockRepository;
import com.ttg.devknowledgeplatform.social.repository.spec.UserSpecification;
import com.ttg.devknowledgeplatform.social.service.FriendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class FriendServiceImpl implements FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final SocialProfileRepository socialProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public FriendRequest sendRequest(Integer requesterId, String addresseeUuid) {
        SocialProfile requester = resolveUser(requesterId);
        SocialProfile addressee = resolveVisibleTarget(requester, addresseeUuid);

        Validator.isFalse(requester.getId().equals(addressee.getId()), SocialErrorCode.CANNOT_FRIEND_SELF);
        Validator.isFalse(userBlockRepository.existsByBlockerAndBlocked(requester, addressee),
                SocialErrorCode.USER_ALREADY_BLOCKED, "You have blocked this user — unblock them first");

        SocialProfile[] pair = canonicalize(requester, addressee);
        Validator.isFalse(friendshipRepository.existsByUser1AndUser2(pair[0], pair[1]),
                SocialErrorCode.ALREADY_FRIENDS, "You are already friends with this user");
        Validator.isFalse(
                friendRequestRepository.findByRequesterAndAddresseeAndStatus(requester, addressee, FriendRequestStatus.PENDING).isPresent(),
                SocialErrorCode.FRIEND_REQUEST_ALREADY_EXISTS, "A pending request to this user already exists");

        var reverseRequest = friendRequestRepository.findByRequesterAndAddresseeAndStatus(
                addressee, requester, FriendRequestStatus.PENDING);
        if (reverseRequest.isPresent()) {
            FriendRequest accepted = reverseRequest.get();
            accepted.setStatus(FriendRequestStatus.ACCEPTED);
            friendRequestRepository.save(accepted);
            createFriendship(pair);
            log.info("Auto-accepted mutual friend request between users {} and {}", requester.getId(), addressee.getId());
            return accepted;
        }

        FriendRequest request = FriendRequest.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendRequestStatus.PENDING)
                .build();
        FriendRequest saved = friendRequestRepository.save(request);
        eventPublisher.publishEvent(new FriendRequestSentEvent(saved.getId(), requester.getId(), addressee.getId()));
        log.info("User {} sent friend request {} to user {}", requester.getId(), saved.getId(), addressee.getId());
        return saved;
    }

    @Override
    public FriendRequest acceptRequest(Integer requestId, Integer actingUserId) {
        FriendRequest request = findPendingRequestAsAddressee(requestId, actingUserId);
        request.setStatus(FriendRequestStatus.ACCEPTED);
        FriendRequest saved = friendRequestRepository.save(request);
        createFriendship(canonicalize(request.getRequester(), request.getAddressee()));
        log.info("User {} accepted friend request {}", actingUserId, requestId);
        return saved;
    }

    @Override
    public FriendRequest rejectRequest(Integer requestId, Integer actingUserId) {
        FriendRequest request = findPendingRequestAsAddressee(requestId, actingUserId);
        request.setStatus(FriendRequestStatus.REJECTED);
        log.info("User {} rejected friend request {}", actingUserId, requestId);
        return friendRequestRepository.save(request);
    }

    @Override
    public FriendRequest cancelRequest(Integer requestId, Integer actingUserId) {
        FriendRequest request = Validator.notFound(friendRequestRepository.findById(requestId), SocialErrorCode.FRIEND_REQUEST_NOT_FOUND);
        Validator.isTrue(request.getRequester().getId().equals(actingUserId), SocialErrorCode.FRIEND_REQUEST_NOT_FOUND);
        requirePending(request);
        request.setStatus(FriendRequestStatus.CANCELLED);
        log.info("User {} cancelled friend request {}", actingUserId, requestId);
        return friendRequestRepository.save(request);
    }

    @Override
    public void unfriend(Integer userId, String otherUserUuid) {
        SocialProfile user = resolveUser(userId);
        SocialProfile other = resolveVisibleTarget(user, otherUserUuid);
        SocialProfile[] pair = canonicalize(user, other);
        Validator.isTrue(friendshipRepository.existsByUser1AndUser2(pair[0], pair[1]),
                SocialErrorCode.NOT_FRIENDS, "You are not friends with this user");
        friendshipRepository.deleteByUser1AndUser2(pair[0], pair[1]);
        log.info("User {} unfriended user {}", userId, other.getId());
    }

    @Override
    public UserBlock block(Integer blockerId, String blockedUuid) {
        SocialProfile blocker = resolveUser(blockerId);
        SocialProfile blocked = resolveUserByUuid(blockedUuid);
        Validator.isFalse(blocker.getId().equals(blocked.getId()), SocialErrorCode.CANNOT_BLOCK_SELF);
        Validator.isFalse(userBlockRepository.existsByBlockerAndBlocked(blocker, blocked), SocialErrorCode.USER_ALREADY_BLOCKED);

        SocialProfile[] pair = canonicalize(blocker, blocked);
        friendshipRepository.deleteByUser1AndUser2(pair[0], pair[1]);
        friendRequestRepository.findPendingBetween(blocker, blocked).ifPresent(pending -> {
            pending.setStatus(FriendRequestStatus.CANCELLED);
            friendRequestRepository.save(pending);
        });

        UserBlock savedBlock = userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(blocked).build());
        log.info("User {} blocked user {}", blockerId, blocked.getId());
        return savedBlock;
    }

    @Override
    public void unblock(Integer blockerId, String blockedUuid) {
        SocialProfile blocker = resolveUser(blockerId);
        SocialProfile blocked = resolveUserByUuid(blockedUuid);
        userBlockRepository.deleteByBlockerAndBlocked(blocker, blocked);
        log.info("User {} unblocked user {}", blockerId, blocked.getId());
    }

    @Override
    public RelationshipStatus getRelationshipStatus(Integer viewerId, String targetUuid) {
        SocialProfile viewer = resolveUser(viewerId);
        SocialProfile target = resolveVisibleTarget(viewer, targetUuid);

        if (viewer.getId().equals(target.getId())) {
            return RelationshipStatus.STRANGER;
        }
        if (userBlockRepository.existsByBlockerAndBlocked(viewer, target)) {
            return RelationshipStatus.BLOCKED;
        }

        SocialProfile[] pair = canonicalize(viewer, target);
        if (friendshipRepository.existsByUser1AndUser2(pair[0], pair[1])) {
            return RelationshipStatus.FRIENDS;
        }
        if (friendRequestRepository.findByRequesterAndAddresseeAndStatus(viewer, target, FriendRequestStatus.PENDING).isPresent()) {
            return RelationshipStatus.REQUEST_SENT;
        }
        if (friendRequestRepository.findByRequesterAndAddresseeAndStatus(target, viewer, FriendRequestStatus.PENDING).isPresent()) {
            return RelationshipStatus.REQUEST_RECEIVED;
        }
        return RelationshipStatus.STRANGER;
    }

    @Override
    public long countMutualFriends(Integer viewerId, String targetUuid) {
        SocialProfile viewer = resolveUser(viewerId);
        SocialProfile target = resolveVisibleTarget(viewer, targetUuid);
        Set<Integer> viewerFriends = new HashSet<>(friendshipRepository.findFriendUserIds(viewer));
        viewerFriends.retainAll(new HashSet<>(friendshipRepository.findFriendUserIds(target)));
        return viewerFriends.size();
    }

    @Override
    public Page<Friendship> listFriends(Integer userId, Pageable pageable) {
        return friendshipRepository.findAllForUser(resolveUser(userId), pageable);
    }

    @Override
    public Page<FriendRequest> listIncomingRequests(Integer userId, Pageable pageable) {
        return friendRequestRepository.findByAddresseeAndStatus(resolveUser(userId), FriendRequestStatus.PENDING, pageable);
    }

    @Override
    public Page<FriendRequest> listOutgoingRequests(Integer userId, Pageable pageable) {
        return friendRequestRepository.findByRequesterAndStatus(resolveUser(userId), FriendRequestStatus.PENDING, pageable);
    }

    @Override
    public Page<SocialProfile> listBlockedUsers(Integer blockerId, Pageable pageable) {
        return userBlockRepository.findByBlocker(resolveUser(blockerId), pageable).map(UserBlock::getBlocked);
    }

    @Override
    public Page<SocialProfile> searchUsers(Integer viewerId, String q, Pageable pageable) {
        Specification<SocialProfile> spec = UserSpecification.search(q, viewerId);
        return socialProfileRepository.findAll(spec, pageable);
    }

    private void createFriendship(SocialProfile[] canonicalPair) {
        Friendship friendship = friendshipRepository.save(
                Friendship.builder().user1(canonicalPair[0]).user2(canonicalPair[1]).build());
        eventPublisher.publishEvent(
                new FriendRequestAcceptedEvent(friendship.getId(), canonicalPair[0].getId(), canonicalPair[1].getId()));
    }

    private FriendRequest findPendingRequestAsAddressee(Integer requestId, Integer actingUserId) {
        FriendRequest request = Validator.notFound(friendRequestRepository.findById(requestId), SocialErrorCode.FRIEND_REQUEST_NOT_FOUND);
        Validator.isTrue(request.getAddressee().getId().equals(actingUserId), SocialErrorCode.FRIEND_REQUEST_NOT_FOUND);
        requirePending(request);
        return request;
    }

    /**
     * Java 21 exhaustive switch (no {@code default}) over {@link FriendRequestStatus} — adding a
     * new status value becomes a compile error here until this method is updated, standing in
     * for a full State-pattern class hierarchy at a scale that doesn't justify one.
     */
    private void requirePending(FriendRequest request) {
        boolean actionable = switch (request.getStatus()) {
            case PENDING -> true;
            case ACCEPTED, REJECTED, CANCELLED -> false;
        };
        Validator.isTrue(actionable, SocialErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request is no longer pending");
    }

    private SocialProfile[] canonicalize(SocialProfile a, SocialProfile b) {
        return a.getId() < b.getId() ? new SocialProfile[] {a, b} : new SocialProfile[] {b, a};
    }

    private SocialProfile resolveUser(Integer userId) {
        return Validator.notFound(socialProfileRepository.findById(userId), CommonErrorCode.USER_NOT_FOUND);
    }

    private SocialProfile resolveUserByUuid(String userUuid) {
        return Validator.notFound(
                socialProfileRepository.findByProfileUuid(userUuid), CommonErrorCode.USER_NOT_FOUND, "User not found: " + userUuid);
    }

    /**
     * Resolves a target user for read/relationship actions, preserving mutual invisibility: if
     * {@code target} has blocked {@code viewer}, this throws the same {@code USER_NOT_FOUND}
     * error as a nonexistent UUID rather than a distinguishable "blocked" error.
     */
    private SocialProfile resolveVisibleTarget(SocialProfile viewer, String targetUuid) {
        SocialProfile target = resolveUserByUuid(targetUuid);
        Validator.isFalse(userBlockRepository.existsByBlockerAndBlocked(target, viewer),
                CommonErrorCode.USER_NOT_FOUND, "User not found: " + targetUuid);
        return target;
    }
}
