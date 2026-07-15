package com.ttg.devknowledgeplatform.social.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.ChannelMessage;
import com.ttg.devknowledgeplatform.social.entity.Group;
import com.ttg.devknowledgeplatform.social.entity.GroupMember;
import com.ttg.devknowledgeplatform.social.enums.GroupMemberRole;
import com.ttg.devknowledgeplatform.social.enums.MessageType;
import com.ttg.devknowledgeplatform.social.exception.SocialErrorCode;
import com.ttg.devknowledgeplatform.social.repository.ChannelMessageRepository;
import com.ttg.devknowledgeplatform.social.repository.ChannelRepository;
import com.ttg.devknowledgeplatform.social.repository.GroupMemberRepository;
import com.ttg.devknowledgeplatform.social.repository.GroupRepository;
import com.ttg.devknowledgeplatform.social.service.GroupService;
import com.ttg.devknowledgeplatform.social.service.MessageAttachmentInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMessageRepository channelMessageRepository;
    private final UserRepository userRepository;

    @Override
    public Group createGroup(Integer creatorId, String name) {
        User creator = resolveUser(creatorId);
        Group group = groupRepository.save(Group.builder().name(name).build());
        groupMemberRepository.save(GroupMember.builder().group(group).user(creator).role(GroupMemberRole.OWNER).build());
        log.info("User {} created group {} ({})", creatorId, group.getId(), name);
        return group;
    }

    @Override
    public GroupMember addMember(Integer actingUserId, Integer groupId, String newMemberUuid) {
        Group group = resolveGroup(groupId);
        requireManagementRole(resolveMembership(group, resolveUser(actingUserId)).getRole());

        User newMember = resolveUserByUuid(newMemberUuid);
        return groupMemberRepository.findByGroupAndUser(group, newMember)
                .orElseGet(() -> {
                    GroupMember saved = groupMemberRepository.save(
                            GroupMember.builder().group(group).user(newMember).role(GroupMemberRole.MEMBER).build());
                    log.info("User {} added user {} to group {}", actingUserId, newMember.getId(), groupId);
                    return saved;
                });
    }

    @Override
    public void removeMember(Integer actingUserId, Integer groupId, String targetUserUuid) {
        Group group = resolveGroup(groupId);
        GroupMemberRole actingRole = resolveMembership(group, resolveUser(actingUserId)).getRole();
        requireManagementRole(actingRole);

        User target = resolveUserByUuid(targetUserUuid);
        GroupMember targetMembership = groupMemberRepository.findByGroupAndUser(group, target)
                .orElseThrow(() -> new ResourceNotFoundException(SocialErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (targetMembership.getRole() == GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.CANNOT_REMOVE_OWNER);
        }
        if (targetMembership.getRole() == GroupMemberRole.ADMIN && actingRole != GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.INSUFFICIENT_GROUP_ROLE, "Only the owner can remove an admin");
        }

        groupMemberRepository.delete(targetMembership);
        log.info("User {} removed user {} from group {}", actingUserId, target.getId(), groupId);
    }

    @Override
    public void leaveGroup(Integer userId, Integer groupId) {
        Group group = resolveGroup(groupId);
        GroupMember membership = resolveMembership(group, resolveUser(userId));
        if (membership.getRole() == GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.OWNER_CANNOT_LEAVE_GROUP);
        }
        groupMemberRepository.delete(membership);
        log.info("User {} left group {}", userId, groupId);
    }

    @Override
    public GroupMember changeRole(Integer actingUserId, Integer groupId, String targetUserUuid, GroupMemberRole newRole) {
        Group group = resolveGroup(groupId);
        GroupMemberRole actingRole = resolveMembership(group, resolveUser(actingUserId)).getRole();
        if (actingRole != GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.INSUFFICIENT_GROUP_ROLE, "Only the owner can change member roles");
        }
        if (newRole == GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.CANNOT_CHANGE_OWNER_ROLE);
        }

        User target = resolveUserByUuid(targetUserUuid);
        GroupMember targetMembership = groupMemberRepository.findByGroupAndUser(group, target)
                .orElseThrow(() -> new ResourceNotFoundException(SocialErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (targetMembership.getRole() == GroupMemberRole.OWNER) {
            throw new BusinessException(SocialErrorCode.CANNOT_CHANGE_OWNER_ROLE);
        }

        targetMembership.setRole(newRole);
        GroupMember saved = groupMemberRepository.save(targetMembership);
        log.info("User {} changed user {}'s role to {} in group {}", actingUserId, target.getId(), newRole, groupId);
        return saved;
    }

    @Override
    public Channel createChannel(Integer actingUserId, Integer groupId, String name) {
        Group group = resolveGroup(groupId);
        requireManagementRole(resolveMembership(group, resolveUser(actingUserId)).getRole());

        if (channelRepository.existsByGroupAndName(group, name)) {
            throw new BusinessException(SocialErrorCode.CHANNEL_NAME_ALREADY_EXISTS);
        }
        Channel saved = channelRepository.save(Channel.builder().group(group).name(name).build());
        log.info("User {} created channel {} ({}) in group {}", actingUserId, saved.getId(), name, groupId);
        return saved;
    }

    @Override
    public ChannelMessage postMessage(Integer senderId, Integer channelId, String content, MessageAttachmentInput attachment) {
        if ((content == null || content.isBlank()) && attachment == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FIELD_INVALID, "Message must have text or an attachment");
        }

        Channel channel = resolveChannel(channelId);
        User sender = resolveUser(senderId);
        resolveMembership(channel.getGroup(), sender);

        ChannelMessage saved = channelMessageRepository.save(ChannelMessage.builder()
                .channel(channel)
                .sender(sender)
                .messageType(resolveMessageType(attachment))
                .content(content)
                .attachmentObjectKey(attachment != null ? attachment.objectKey() : null)
                .attachmentMimeType(attachment != null ? attachment.mimeType() : null)
                .attachmentFileName(attachment != null ? attachment.fileName() : null)
                .attachmentFileSize(attachment != null ? attachment.fileSize() : null)
                .build());
        log.info("User {} posted message {} in channel {}", senderId, saved.getId(), channelId);
        return saved;
    }

    @Override
    public Page<Group> listMyGroups(Integer userId, Pageable pageable) {
        return groupRepository.findAllForUser(resolveUser(userId), pageable);
    }

    @Override
    public List<Channel> listChannels(Integer userId, Integer groupId) {
        Group group = resolveGroup(groupId);
        resolveMembership(group, resolveUser(userId));
        return channelRepository.findByGroup(group);
    }

    @Override
    public Page<ChannelMessage> listMessages(Integer userId, Integer channelId, Pageable pageable) {
        Channel channel = resolveChannel(channelId);
        resolveMembership(channel.getGroup(), resolveUser(userId));
        return channelMessageRepository.findByChannelOrderByDteCreationDesc(channel, pageable);
    }

    @Override
    public boolean isChannelMember(Integer userId, Integer channelId) {
        return channelRepository.findById(channelId)
                .flatMap(channel -> userRepository.findById(userId)
                        .map(user -> groupMemberRepository.existsByGroupAndUser(channel.getGroup(), user)))
                .orElse(false);
    }

    /**
     * Java 21 exhaustive switch (no {@code default}) over {@link GroupMemberRole} — adding a new
     * role becomes a compile error here until this method is updated, same technique
     * {@code FriendServiceImpl.requirePending} uses for {@code FriendRequestStatus}, standing in
     * for a full State-pattern class hierarchy at a scale that doesn't justify one.
     */
    private void requireManagementRole(GroupMemberRole role) {
        boolean canManage = switch (role) {
            case OWNER, ADMIN -> true;
            case MEMBER -> false;
        };
        if (!canManage) {
            throw new BusinessException(SocialErrorCode.INSUFFICIENT_GROUP_ROLE);
        }
    }

    private GroupMember resolveMembership(Group group, User user) {
        return groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new BusinessException(SocialErrorCode.NOT_GROUP_MEMBER));
    }

    private static MessageType resolveMessageType(MessageAttachmentInput attachment) {
        if (attachment == null) {
            return MessageType.TEXT;
        }
        return attachment.mimeType() != null && attachment.mimeType().startsWith("image/")
                ? MessageType.IMAGE
                : MessageType.FILE;
    }

    private Group resolveGroup(Integer groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(SocialErrorCode.GROUP_NOT_FOUND));
    }

    private Channel resolveChannel(Integer channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException(SocialErrorCode.CHANNEL_NOT_FOUND));
    }

    private User resolveUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND));
    }

    private User resolveUserByUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
    }
}
