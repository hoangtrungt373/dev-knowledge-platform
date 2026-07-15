package com.ttg.devknowledgeplatform.social.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.Group;
import com.ttg.devknowledgeplatform.social.entity.GroupMember;
import com.ttg.devknowledgeplatform.social.enums.GroupMemberRole;
import com.ttg.devknowledgeplatform.social.exception.SocialErrorCode;
import com.ttg.devknowledgeplatform.social.repository.ChannelMessageRepository;
import com.ttg.devknowledgeplatform.social.repository.ChannelRepository;
import com.ttg.devknowledgeplatform.social.repository.GroupMemberRepository;
import com.ttg.devknowledgeplatform.social.repository.GroupRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelMessageRepository channelMessageRepository;
    @Mock
    private UserRepository userRepository;

    private GroupServiceImpl groupService;

    private User alice;
    private User bob;
    private User carla;
    private Group group;

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
                groupRepository, groupMemberRepository, channelRepository, channelMessageRepository, userRepository);

        alice = User.builder().build();
        alice.setId(1);
        alice.setUserUuid("alice-uuid");

        bob = User.builder().build();
        bob.setId(2);
        bob.setUserUuid("bob-uuid");

        carla = User.builder().build();
        carla.setId(3);
        carla.setUserUuid("carla-uuid");

        group = Group.builder().name("Backend Guild").build();
        group.setId(10);
    }

    @Test
    void createGroup_createsGroupAndOwnerMembership() {
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        Group result = groupService.createGroup(1, "Backend Guild");

        assertThat(result.getName()).isEqualTo("Backend Guild");
        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(alice);
        assertThat(captor.getValue().getRole()).isEqualTo(GroupMemberRole.OWNER);
    }

    @Test
    void addMember_asOwner_addsNewMemberAsRegularMember() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByUserUuid("carla-uuid")).thenReturn(Optional.of(carla));
        when(groupMemberRepository.findByGroupAndUser(group, carla)).thenReturn(Optional.empty());
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember result = groupService.addMember(1, 10, "carla-uuid");

        assertThat(result.getUser()).isSameAs(carla);
        assertThat(result.getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    void addMember_whenAlreadyMember_returnsExistingMembershipWithoutSavingAgain() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        GroupMember existingMembership = GroupMember.builder().group(group).user(carla).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByUserUuid("carla-uuid")).thenReturn(Optional.of(carla));
        when(groupMemberRepository.findByGroupAndUser(group, carla)).thenReturn(Optional.of(existingMembership));

        GroupMember result = groupService.addMember(1, 10, "carla-uuid");

        assertThat(result).isSameAs(existingMembership);
        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void addMember_whenActingUserIsPlainMember_throwsInsufficientRole() {
        GroupMember memberMembership = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> groupService.addMember(2, 10, "carla-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.INSUFFICIENT_GROUP_ROLE);
    }

    @Test
    void removeMember_targetIsOwner_throwsCannotRemoveOwner() {
        GroupMember adminMembership = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.ADMIN).build();
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(adminMembership));
        when(userRepository.findByUserUuid("alice-uuid")).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> groupService.removeMember(2, 10, "alice-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.CANNOT_REMOVE_OWNER);
    }

    @Test
    void removeMember_adminRemovingAnotherAdmin_throwsInsufficientRole() {
        GroupMember actingAdmin = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.ADMIN).build();
        GroupMember targetAdmin = GroupMember.builder().group(group).user(carla).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(actingAdmin));
        when(userRepository.findByUserUuid("carla-uuid")).thenReturn(Optional.of(carla));
        when(groupMemberRepository.findByGroupAndUser(group, carla)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> groupService.removeMember(2, 10, "carla-uuid"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.INSUFFICIENT_GROUP_ROLE);
    }

    @Test
    void removeMember_ownerRemovingAdmin_succeeds() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        GroupMember targetAdmin = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByUserUuid("bob-uuid")).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(targetAdmin));

        groupService.removeMember(1, 10, "bob-uuid");

        verify(groupMemberRepository).delete(targetAdmin);
    }

    @Test
    void leaveGroup_asOwner_throwsOwnerCannotLeave() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> groupService.leaveGroup(1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.OWNER_CANNOT_LEAVE_GROUP);
    }

    @Test
    void leaveGroup_asPlainMember_deletesMembership() {
        GroupMember memberMembership = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(memberMembership));

        groupService.leaveGroup(2, 10);

        verify(groupMemberRepository).delete(memberMembership);
    }

    @Test
    void changeRole_asNonOwner_throwsInsufficientRole() {
        GroupMember adminMembership = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.ADMIN).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> groupService.changeRole(2, 10, "carla-uuid", GroupMemberRole.ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.INSUFFICIENT_GROUP_ROLE);
    }

    @Test
    void changeRole_toOwner_throwsCannotChangeOwnerRole() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));

        assertThatThrownBy(() -> groupService.changeRole(1, 10, "carla-uuid", GroupMemberRole.OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.CANNOT_CHANGE_OWNER_ROLE);
    }

    @Test
    void changeRole_promotesMemberToAdmin() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        GroupMember targetMembership = GroupMember.builder().group(group).user(carla).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByUserUuid("carla-uuid")).thenReturn(Optional.of(carla));
        when(groupMemberRepository.findByGroupAndUser(group, carla)).thenReturn(Optional.of(targetMembership));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMember result = groupService.changeRole(1, 10, "carla-uuid", GroupMemberRole.ADMIN);

        assertThat(result.getRole()).isEqualTo(GroupMemberRole.ADMIN);
    }

    @Test
    void createChannel_whenNameAlreadyExists_throwsChannelNameAlreadyExists() {
        GroupMember ownerMembership = GroupMember.builder().group(group).user(alice).role(GroupMemberRole.OWNER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.findByGroupAndUser(group, alice)).thenReturn(Optional.of(ownerMembership));
        when(channelRepository.existsByGroupAndName(group, "general")).thenReturn(true);

        assertThatThrownBy(() -> groupService.createChannel(1, 10, "general"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.CHANNEL_NAME_ALREADY_EXISTS);
    }

    @Test
    void createChannel_asPlainMember_throwsInsufficientRole() {
        GroupMember memberMembership = GroupMember.builder().group(group).user(bob).role(GroupMemberRole.MEMBER).build();
        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userRepository.findById(2)).thenReturn(Optional.of(bob));
        when(groupMemberRepository.findByGroupAndUser(group, bob)).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> groupService.createChannel(2, 10, "general"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.INSUFFICIENT_GROUP_ROLE);
    }

    @Test
    void postMessage_withNoContentAndNoAttachment_throwsValidationError() {
        assertThatThrownBy(() -> groupService.postMessage(1, 20, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void postMessage_asNonGroupMember_throwsNotGroupMember() {
        Channel channel = Channel.builder().group(group).name("general").build();
        channel.setId(20);
        when(channelRepository.findById(20)).thenReturn(Optional.of(channel));
        when(userRepository.findById(3)).thenReturn(Optional.of(carla));
        when(groupMemberRepository.findByGroupAndUser(group, carla)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.postMessage(3, 20, "hello", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    void createChannel_whenGroupNotFound_throwsGroupNotFound() {
        when(groupRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.createChannel(1, 999, "general"))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(SocialErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void isChannelMember_whenMember_returnsTrue() {
        Channel channel = Channel.builder().group(group).name("general").build();
        channel.setId(20);
        when(channelRepository.findById(20)).thenReturn(Optional.of(channel));
        when(userRepository.findById(1)).thenReturn(Optional.of(alice));
        when(groupMemberRepository.existsByGroupAndUser(group, alice)).thenReturn(true);

        assertThat(groupService.isChannelMember(1, 20)).isTrue();
    }

    @Test
    void isChannelMember_whenNotMember_returnsFalse() {
        Channel channel = Channel.builder().group(group).name("general").build();
        channel.setId(20);
        when(channelRepository.findById(20)).thenReturn(Optional.of(channel));
        when(userRepository.findById(3)).thenReturn(Optional.of(carla));
        when(groupMemberRepository.existsByGroupAndUser(group, carla)).thenReturn(false);

        assertThat(groupService.isChannelMember(3, 20)).isFalse();
    }

    @Test
    void isChannelMember_whenChannelDoesNotExist_returnsFalseWithoutThrowing() {
        when(channelRepository.findById(999)).thenReturn(Optional.empty());

        assertThat(groupService.isChannelMember(1, 999)).isFalse();
    }
}
