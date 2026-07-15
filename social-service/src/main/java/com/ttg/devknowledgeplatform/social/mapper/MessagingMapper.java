package com.ttg.devknowledgeplatform.social.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.infra.service.StorageService;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChannelMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChannelResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmThreadResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.GroupMemberResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.GroupResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.MessageAttachmentRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.MessageAttachmentResponse;
import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.ChannelMessage;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;
import com.ttg.devknowledgeplatform.social.entity.Group;
import com.ttg.devknowledgeplatform.social.entity.GroupMember;
import com.ttg.devknowledgeplatform.social.service.MessageAttachmentInput;

/**
 * Maps chat entities from {@code social-service} (groups/channels and DMs) to REST response
 * records.
 *
 * <p>An abstract class rather than a plain interface — like {@link FriendMapper} — because
 * attachment presigned-URL resolution needs an injected {@link StorageService}, and MapStruct
 * interfaces can't hold instance fields. Uses {@link FriendMapper} for {@code User} →
 * {@link com.ttg.devknowledgeplatform.social.dto.friend.UserSummaryResponse} so avatar
 * presigned-URL resolution isn't duplicated here.
 */
@Mapper(componentModel = "spring", uses = FriendMapper.class)
public abstract class MessagingMapper {

    @Autowired
    protected StorageService storageService;

    @Autowired
    protected FriendMapper friendMapper;

    public abstract GroupResponse toGroupResponse(Group group);

    /** {@code null} in, {@code null} out — MapStruct's default for a null source object. */
    public abstract MessageAttachmentInput toAttachmentInput(MessageAttachmentRequest request);

    @Mapping(target = "role", expression = "java(groupMember.getRole().name())")
    @Mapping(target = "joinedAt", source = "dteCreation")
    public abstract GroupMemberResponse toGroupMemberResponse(GroupMember groupMember);

    @Mapping(target = "groupId", expression = "java(channel.getGroup().getId())")
    public abstract ChannelResponse toChannelResponse(Channel channel);

    @Mapping(target = "channelId", expression = "java(message.getChannel().getId())")
    @Mapping(target = "messageType", expression = "java(message.getMessageType().name())")
    @Mapping(target = "attachment", expression = "java(toAttachment(message.getAttachmentObjectKey(), "
            + "message.getAttachmentMimeType(), message.getAttachmentFileName(), message.getAttachmentFileSize()))")
    @Mapping(target = "createdAt", source = "dteCreation")
    public abstract ChannelMessageResponse toChannelMessageResponse(ChannelMessage message);

    @Mapping(target = "otherUser", expression = "java(friendMapper.toUserSummary(otherUser(thread, viewerId)))")
    public abstract DmThreadResponse toDmThreadResponse(DmThread thread, @Context Integer viewerId);

    @Mapping(target = "threadId", expression = "java(message.getDmThread().getId())")
    @Mapping(target = "messageType", expression = "java(message.getMessageType().name())")
    @Mapping(target = "attachment", expression = "java(toAttachment(message.getAttachmentObjectKey(), "
            + "message.getAttachmentMimeType(), message.getAttachmentFileName(), message.getAttachmentFileSize()))")
    @Mapping(target = "createdAt", source = "dteCreation")
    public abstract DmMessageResponse toDmMessageResponse(DmMessage message);

    protected MessageAttachmentResponse toAttachment(String objectKey, String mimeType, String fileName, Long fileSize) {
        if (objectKey == null) {
            return null;
        }
        return new MessageAttachmentResponse(storageService.getPresignedUrl(objectKey), mimeType, fileName, fileSize);
    }

    protected User otherUser(DmThread thread, Integer viewerId) {
        return thread.getUser1().getId().equals(viewerId) ? thread.getUser2() : thread.getUser1();
    }
}
