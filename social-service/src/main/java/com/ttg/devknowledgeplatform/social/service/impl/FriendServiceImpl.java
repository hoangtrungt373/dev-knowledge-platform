package com.ttg.devknowledgeplatform.social.service.impl;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;
import com.ttg.devknowledgeplatform.social.event.FriendRequestAcceptedEvent;
import com.ttg.devknowledgeplatform.social.event.FriendRequestSentEvent;
import com.ttg.devknowledgeplatform.social.repository.FriendRequestRepository;
import com.ttg.devknowledgeplatform.social.repository.FriendshipRepository;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public FriendRequest sendRequest(Integer requesterId, String addresseeUuid) {
        User requester = resolveUser(requesterId);
        User addressee = resolveVisibleTarget(requester, addresseeUuid);

        if (requester.getId().equals(addressee.getId())) {
            throw new BusinessException(CommonErrorCode.CANNOT_FRIEND_SELF, "You cannot send a friend request to yourself");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(requester, addressee)) {
            throw new BusinessException(CommonErrorCode.USER_ALREADY_BLOCKED, "You have blocked this user — unblock them first");
        }

        User[] pair = canonicalize(requester, addressee);
        if (friendshipRepository.existsByUser1AndUser2(pair[0], pair[1])) {
            throw new BusinessException(CommonErrorCode.ALREADY_FRIENDS, "You are already friends with this user");
        }
        if (friendRequestRepository.findByRequesterAndAddresseeAndStatus(requester, addressee, FriendRequestStatus.PENDING).isPresent()) {
            throw new BusinessException(CommonErrorCode.FRIEND_REQUEST_ALREADY_EXISTS, "A pending request to this user already exists");
        }

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
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request not found"));
        if (!request.getRequester().getId().equals(actingUserId)) {
            throw new ResourceNotFoundException(CommonErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request not found");
        }
        requirePending(request);
        request.setStatus(FriendRequestStatus.CANCELLED);
        log.info("User {} cancelled friend request {}", actingUserId, requestId);
        return friendRequestRepository.save(request);
    }

    @Override
    public void unfriend(Integer userId, String otherUserUuid) {
        User user = resolveUser(userId);
        User other = resolveVisibleTarget(user, otherUserUuid);
        User[] pair = canonicalize(user, other);
        if (!friendshipRepository.existsByUser1AndUser2(pair[0], pair[1])) {
            throw new BusinessException(CommonErrorCode.NOT_FRIENDS, "You are not friends with this user");
        }
        friendshipRepository.deleteByUser1AndUser2(pair[0], pair[1]);
        log.info("User {} unfriended user {}", userId, other.getId());
    }

    @Override
    public UserBlock block(Integer blockerId, String blockedUuid) {
        User blocker = resolveUser(blockerId);
        User blocked = resolveUserByUuid(blockedUuid);
        if (blocker.getId().equals(blocked.getId())) {
            throw new BusinessException(CommonErrorCode.CANNOT_FRIEND_SELF, "You cannot block yourself");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            throw new BusinessException(CommonErrorCode.USER_ALREADY_BLOCKED, "This user is already blocked");
        }

        User[] pair = canonicalize(blocker, blocked);
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
        User blocker = resolveUser(blockerId);
        User blocked = resolveUserByUuid(blockedUuid);
        userBlockRepository.deleteByBlockerAndBlocked(blocker, blocked);
        log.info("User {} unblocked user {}", blockerId, blocked.getId());
    }

    @Override
    public RelationshipStatus getRelationshipStatus(Integer viewerId, String targetUuid) {
        User viewer = resolveUser(viewerId);
        User target = resolveVisibleTarget(viewer, targetUuid);

        if (viewer.getId().equals(target.getId())) {
            return RelationshipStatus.STRANGER;
        }
        if (userBlockRepository.existsByBlockerAndBlocked(viewer, target)) {
            return RelationshipStatus.BLOCKED;
        }

        User[] pair = canonicalize(viewer, target);
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
        User viewer = resolveUser(viewerId);
        User target = resolveVisibleTarget(viewer, targetUuid);
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
    public Page<User> listBlockedUsers(Integer blockerId, Pageable pageable) {
        return userBlockRepository.findByBlocker(resolveUser(blockerId), pageable).map(UserBlock::getBlocked);
    }

    @Override
    public Page<User> searchUsers(Integer viewerId, String q, Pageable pageable) {
        Specification<User> spec = UserSpecification.search(q, viewerId);
        return userRepository.findAll(spec, pageable);
    }

    private void createFriendship(User[] canonicalPair) {
        Friendship friendship = friendshipRepository.save(
                Friendship.builder().user1(canonicalPair[0]).user2(canonicalPair[1]).build());
        eventPublisher.publishEvent(
                new FriendRequestAcceptedEvent(friendship.getId(), canonicalPair[0].getId(), canonicalPair[1].getId()));
    }

    private FriendRequest findPendingRequestAsAddressee(Integer requestId, Integer actingUserId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request not found"));
        if (!request.getAddressee().getId().equals(actingUserId)) {
            throw new ResourceNotFoundException(CommonErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request not found");
        }
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
        if (!actionable) {
            throw new ApiException(CommonErrorCode.FRIEND_REQUEST_NOT_FOUND, "Friend request is no longer pending");
        }
    }

    private User[] canonicalize(User a, User b) {
        return a.getId() < b.getId() ? new User[] {a, b} : new User[] {b, a};
    }

    private User resolveUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND, "User not found"));
    }

    private User resolveUserByUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
    }

    /**
     * Resolves a target user for read/relationship actions, preserving mutual invisibility: if
     * {@code target} has blocked {@code viewer}, this throws the same {@code USER_NOT_FOUND}
     * error as a nonexistent UUID rather than a distinguishable "blocked" error.
     */
    private User resolveVisibleTarget(User viewer, String targetUuid) {
        User target = resolveUserByUuid(targetUuid);
        if (userBlockRepository.existsByBlockerAndBlocked(target, viewer)) {
            throw new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND, "User not found: " + targetUuid);
        }
        return target;
    }
}
