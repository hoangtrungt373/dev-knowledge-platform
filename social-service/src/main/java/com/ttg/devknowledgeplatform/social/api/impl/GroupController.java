package com.ttg.devknowledgeplatform.social.api.impl;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.social.api.GroupApi;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChangeRoleRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChannelMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChannelResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.CreateChannelRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.CreateGroupRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.GroupMemberResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.GroupResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;
import com.ttg.devknowledgeplatform.social.mapper.MessagingMapper;
import com.ttg.devknowledgeplatform.social.enums.GroupMemberRole;
import com.ttg.devknowledgeplatform.social.service.GroupService;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GroupApi}.
 */
@RestController
@RequiredArgsConstructor
public class GroupController implements GroupApi {

    private final GroupService groupService;
    private final MessagingMapper messagingMapper;

    @Override
    public ResponseEntity<GroupResponse> createGroup(Integer userId, CreateGroupRequest request) {
        var group = groupService.createGroup(userId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(messagingMapper.toGroupResponse(group));
    }

    @Override
    public ResponseEntity<PagedResponse<GroupResponse>> listMyGroups(Integer userId, int page, int size) {
        var result = groupService.listMyGroups(userId, PageRequest.of(page, size))
                .map(messagingMapper::toGroupResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<GroupMemberResponse> addMember(Integer userId, Integer groupId, String userUuid) {
        var member = groupService.addMember(userId, groupId, userUuid);
        return ResponseEntity.ok(messagingMapper.toGroupMemberResponse(member));
    }

    @Override
    public ResponseEntity<Void> removeMember(Integer userId, Integer groupId, String userUuid) {
        groupService.removeMember(userId, groupId, userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> leaveGroup(Integer userId, Integer groupId) {
        groupService.leaveGroup(userId, groupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GroupMemberResponse> changeRole(Integer userId, Integer groupId, String userUuid, ChangeRoleRequest request) {
        var member = groupService.changeRole(userId, groupId, userUuid, parseRole(request.role()));
        return ResponseEntity.ok(messagingMapper.toGroupMemberResponse(member));
    }

    @Override
    public ResponseEntity<ChannelResponse> createChannel(Integer userId, Integer groupId, CreateChannelRequest request) {
        var channel = groupService.createChannel(userId, groupId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(messagingMapper.toChannelResponse(channel));
    }

    @Override
    public ResponseEntity<List<ChannelResponse>> listChannels(Integer userId, Integer groupId) {
        var channels = groupService.listChannels(userId, groupId).stream()
                .map(messagingMapper::toChannelResponse)
                .toList();
        return ResponseEntity.ok(channels);
    }

    @Override
    public ResponseEntity<ChannelMessageResponse> postMessage(Integer userId, Integer channelId, SendMessageRequest request) {
        var attachment = messagingMapper.toAttachmentInput(request.attachment());
        var message = groupService.postMessage(userId, channelId, request.content(), attachment);
        return ResponseEntity.status(HttpStatus.CREATED).body(messagingMapper.toChannelMessageResponse(message));
    }

    @Override
    public ResponseEntity<PagedResponse<ChannelMessageResponse>> listMessages(Integer userId, Integer channelId, int page, int size) {
        var result = groupService.listMessages(userId, channelId, PageRequest.of(page, size))
                .map(messagingMapper::toChannelMessageResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private GroupMemberRole parseRole(String role) {
        try {
            return GroupMemberRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FIELD_INVALID, "Invalid role: " + role);
        }
    }
}
