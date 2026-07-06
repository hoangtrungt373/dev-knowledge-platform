package com.ttg.devknowledgeplatform.social.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;
import com.ttg.devknowledgeplatform.social.repository.FriendRequestRepository;
import com.ttg.devknowledgeplatform.social.repository.FriendshipRepository;
import com.ttg.devknowledgeplatform.social.repository.SocialUserRepository;
import com.ttg.devknowledgeplatform.social.repository.UserBlockRepository;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private SocialUserRepository socialUserRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FriendServiceImpl friendService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        friendService = new FriendServiceImpl(
                friendRequestRepository, friendshipRepository, userBlockRepository, socialUserRepository, eventPublisher);

        alice = User.builder().build();
        alice.setId(1);
        alice.setUserUuid("alice-uuid");

        bob = User.builder().build();
        bob.setId(2);
        bob.setUserUuid("bob-uuid");
    }

    @Test
    void sendRequest_toSelf_throwsCannotFriendSelf() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("alice-uuid")).thenReturn(Optional.of(alice));
        when(userBlockRepository.existsByBlockerAndBlocked(alice, alice)).thenReturn(false);

        assertThatThrownBy(() -> friendService.sendRequest(1, "alice-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_FRIEND_SELF);
    }

    @Test
    void sendRequest_whenPendingRequestAlreadyExists_throwsAlreadyExists() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);
        when(userBlockRepository.existsByBlockerAndBlocked(alice, bob)).thenReturn(false);
        when(friendshipRepository.existsByUser1AndUser2(alice, bob)).thenReturn(false);
        when(friendRequestRepository.findByRequesterAndAddresseeAndStatus(alice, bob, FriendRequestStatus.PENDING))
                .thenReturn(Optional.of(FriendRequest.builder().requester(alice).addressee(bob).status(FriendRequestStatus.PENDING).build()));

        assertThatThrownBy(() -> friendService.sendRequest(1, "bob-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
    }

    @Test
    void sendRequest_whenReverseRequestPending_autoAcceptsIntoFriendship() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);
        when(userBlockRepository.existsByBlockerAndBlocked(alice, bob)).thenReturn(false);
        when(friendshipRepository.existsByUser1AndUser2(alice, bob)).thenReturn(false);
        when(friendRequestRepository.findByRequesterAndAddresseeAndStatus(alice, bob, FriendRequestStatus.PENDING))
                .thenReturn(Optional.empty());

        FriendRequest reverseRequest = FriendRequest.builder()
                .requester(bob).addressee(alice).status(FriendRequestStatus.PENDING).build();
        reverseRequest.setId(99);
        when(friendRequestRepository.findByRequesterAndAddresseeAndStatus(bob, alice, FriendRequestStatus.PENDING))
                .thenReturn(Optional.of(reverseRequest));
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Friendship savedFriendship = Friendship.builder().user1(alice).user2(bob).build();
        savedFriendship.setId(5);
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(savedFriendship);

        FriendRequest result = friendService.sendRequest(1, "bob-uuid");

        assertThat(result).isSameAs(reverseRequest);
        assertThat(result.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        verify(friendshipRepository).save(any(Friendship.class));
        verify(friendRequestRepository, times(1)).save(any(FriendRequest.class));
    }

    @Test
    void block_cascadesRemovalOfFriendshipAndPendingRequest() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(alice, bob)).thenReturn(false);
        FriendRequest pending = FriendRequest.builder().requester(bob).addressee(alice).status(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findPendingBetween(alice, bob)).thenReturn(Optional.of(pending));
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        friendService.block(1, "bob-uuid");

        verify(friendshipRepository).deleteByUser1AndUser2(alice, bob);
        assertThat(pending.getStatus()).isEqualTo(FriendRequestStatus.CANCELLED);
        verify(friendRequestRepository).save(pending);
        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    void block_self_throwsCannotFriendSelf() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("alice-uuid")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> friendService.block(1, "alice-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_FRIEND_SELF);
    }

    @Test
    void unfriend_whenNotFriends_throwsNotFriends() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);
        when(friendshipRepository.existsByUser1AndUser2(alice, bob)).thenReturn(false);

        assertThatThrownBy(() -> friendService.unfriend(1, "bob-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FRIENDS);
    }

    @Test
    void unfriend_whenFriends_deletesFriendshipRow() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(false);
        when(friendshipRepository.existsByUser1AndUser2(alice, bob)).thenReturn(true);

        friendService.unfriend(1, "bob-uuid");

        verify(friendshipRepository).deleteByUser1AndUser2(alice, bob);
    }

    @Test
    void unblock_deletesTheBlockRow() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));

        friendService.unblock(1, "bob-uuid");

        verify(userBlockRepository, times(1)).deleteByBlockerAndBlocked(alice, bob);
    }

    @Test
    void resolveVisibleTarget_whenTargetBlockedViewer_throwsNotFoundNotBlocked() {
        when(socialUserRepository.findById(1)).thenReturn(Optional.of(alice));
        when(socialUserRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(userBlockRepository.existsByBlockerAndBlocked(bob, alice)).thenReturn(true);

        assertThatThrownBy(() -> friendService.sendRequest(1, "bob-uuid"))
                .isInstanceOf(com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
