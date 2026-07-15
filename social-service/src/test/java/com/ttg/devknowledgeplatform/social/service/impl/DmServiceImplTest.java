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

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;
import com.ttg.devknowledgeplatform.social.enums.MessageType;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;
import com.ttg.devknowledgeplatform.social.exception.SocialErrorCode;
import com.ttg.devknowledgeplatform.social.repository.DmMessageRepository;
import com.ttg.devknowledgeplatform.social.repository.DmThreadRepository;
import com.ttg.devknowledgeplatform.social.service.FriendService;
import com.ttg.devknowledgeplatform.social.dto.messaging.MessageAttachmentInput;

@ExtendWith(MockitoExtension.class)
class DmServiceImplTest {

    @Mock
    private DmThreadRepository dmThreadRepository;
    @Mock
    private DmMessageRepository dmMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendService friendService;

    private DmServiceImpl dmService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        dmService = new DmServiceImpl(dmThreadRepository, dmMessageRepository, userRepository, friendService);

        alice = User.builder().build();
        alice.setId(1);
        alice.setUserUuid("alice-uuid");

        bob = User.builder().build();
        bob.setId(2);
        bob.setUserUuid("bob-uuid");
    }

    @Test
    void sendMessage_whenNoContentAndNoAttachment_throwsValidationError() {
        assertThatThrownBy(() -> dmService.sendMessage(1, "bob-uuid", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sendMessage_whenNotFriends_throwsDmFriendRequired() {
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(friendService.getRelationshipStatus(1, "bob-uuid")).thenReturn(RelationshipStatus.STRANGER);

        assertThatThrownBy(() -> dmService.sendMessage(1, "bob-uuid", "hi", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.DM_FRIEND_REQUIRED);
    }

    @Test
    void sendMessage_whenBlocked_throwsDmFriendRequired_notADistinguishableBlockedError() {
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(friendService.getRelationshipStatus(1, "bob-uuid")).thenReturn(RelationshipStatus.BLOCKED);

        assertThatThrownBy(() -> dmService.sendMessage(1, "bob-uuid", "hi", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.DM_FRIEND_REQUIRED);
    }

    @Test
    void sendMessage_whenNoExistingThread_createsThreadLazily() {
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(friendService.getRelationshipStatus(1, "bob-uuid")).thenReturn(RelationshipStatus.FRIENDS);
        when(dmThreadRepository.findByUser1AndUser2(alice, bob)).thenReturn(Optional.empty());
        when(dmThreadRepository.save(any(DmThread.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dmMessageRepository.save(any(DmMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        DmMessage result = dmService.sendMessage(1, "bob-uuid", "Hey Bob", null);

        assertThat(result.getContent()).isEqualTo("Hey Bob");
        assertThat(result.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(result.getDmThread().getUser1()).isSameAs(alice);
        assertThat(result.getDmThread().getUser2()).isSameAs(bob);
        // Thread saved twice: once on lazy creation, once for the lastMessageAt update.
        verify(dmThreadRepository, times(2)).save(any(DmThread.class));
    }

    @Test
    void sendMessage_whenThreadAlreadyExists_reusesIt() {
        DmThread existingThread = DmThread.builder().user1(alice).user2(bob).build();
        existingThread.setId(5);
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(friendService.getRelationshipStatus(1, "bob-uuid")).thenReturn(RelationshipStatus.FRIENDS);
        when(dmThreadRepository.findByUser1AndUser2(alice, bob)).thenReturn(Optional.of(existingThread));
        when(dmThreadRepository.save(any(DmThread.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dmMessageRepository.save(any(DmMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        DmMessage result = dmService.sendMessage(1, "bob-uuid", "Hey again", null);

        assertThat(result.getDmThread()).isSameAs(existingThread);
        // Only the lastMessageAt update save — no new thread created.
        verify(dmThreadRepository, times(1)).save(any(DmThread.class));
    }

    @Test
    void sendMessage_withImageAttachment_setsMessageTypeImage() {
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(friendService.getRelationshipStatus(1, "bob-uuid")).thenReturn(RelationshipStatus.FRIENDS);
        when(dmThreadRepository.findByUser1AndUser2(alice, bob)).thenReturn(Optional.empty());
        when(dmThreadRepository.save(any(DmThread.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dmMessageRepository.save(any(DmMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageAttachmentInput attachment = new MessageAttachmentInput("dm/pic.png", "image/png", "pic.png", 1024L);
        DmMessage result = dmService.sendMessage(1, "bob-uuid", null, attachment);

        assertThat(result.getMessageType()).isEqualTo(MessageType.IMAGE);
        assertThat(result.getAttachmentObjectKey()).isEqualTo("dm/pic.png");
    }

    @Test
    void listMessages_whenUserNotAParticipant_throwsThreadNotFound() {
        DmThread thread = DmThread.builder().user1(alice).user2(bob).build();
        thread.setId(5);
        when(dmThreadRepository.findById(5)).thenReturn(Optional.of(thread));

        User carla = User.builder().build();
        carla.setId(3);

        assertThatThrownBy(() -> dmService.listMessages(3, 5, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.DM_THREAD_NOT_FOUND);
    }

    @Test
    void listMessages_whenThreadDoesNotExist_throwsThreadNotFound() {
        when(dmThreadRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dmService.listMessages(1, 999, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.DM_THREAD_NOT_FOUND);
    }
}
