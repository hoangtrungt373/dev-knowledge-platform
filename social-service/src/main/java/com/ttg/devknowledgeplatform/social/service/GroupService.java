package com.ttg.devknowledgeplatform.social.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.ChannelMessage;
import com.ttg.devknowledgeplatform.social.entity.Group;
import com.ttg.devknowledgeplatform.social.entity.GroupMember;
import com.ttg.devknowledgeplatform.social.enums.GroupMemberRole;

/**
 * Owns multi-user group chat: groups, membership/roles, channels, and channel messages — the
 * open-add counterpart to {@link DmService}'s friend-gated 1:1 messaging.
 *
 * <p>Returns entities rather than REST DTOs, same convention as {@link FriendService}.
 */
public interface GroupService {

    /** Creates a group with {@code creatorId} as its sole member, holding {@code OWNER}. */
    Group createGroup(Integer creatorId, String name);

    /**
     * Adds {@code newMemberUuid} to the group as a {@code MEMBER}. Open add: no friendship
     * required, only that {@code actingUserId} holds {@code OWNER}/{@code ADMIN}. Idempotent —
     * adding an existing member returns their current membership rather than erroring.
     */
    GroupMember addMember(Integer actingUserId, Integer groupId, String newMemberUuid);

    /**
     * Removes {@code targetUserUuid} from the group. The owner can never be removed this way; an
     * {@code ADMIN} can only be removed by the {@code OWNER}, not by another {@code ADMIN}.
     */
    void removeMember(Integer actingUserId, Integer groupId, String targetUserUuid);

    /** Removes {@code userId}'s own membership. The owner cannot leave in this MVP. */
    void leaveGroup(Integer userId, Integer groupId);

    /**
     * Changes {@code targetUserUuid}'s role. Only the {@code OWNER} can call this, and ownership
     * itself is not reassignable yet — {@code newRole} must be {@code ADMIN} or {@code MEMBER},
     * and the target must not already be the {@code OWNER}.
     */
    GroupMember changeRole(Integer actingUserId, Integer groupId, String targetUserUuid, GroupMemberRole newRole);

    /** Creates a channel. Requires {@code actingUserId} to hold {@code OWNER}/{@code ADMIN}. */
    Channel createChannel(Integer actingUserId, Integer groupId, String name);

    /**
     * Posts a message to a channel. {@code content}/{@code attachment} follow the same
     * either-or-both rule as {@link DmService#sendMessage}. Requires {@code senderId} to be a
     * member (any role) of the channel's group.
     */
    ChannelMessage postMessage(Integer senderId, Integer channelId, String content, MessageAttachmentInput attachment);

    /** Groups {@code userId} belongs to. */
    Page<Group> listMyGroups(Integer userId, Pageable pageable);

    /** Channels in a group. Requires {@code userId} to be a member. */
    List<Channel> listChannels(Integer userId, Integer groupId);

    /** Paginated message history for a channel. Requires {@code userId} to be a member of its group. */
    Page<ChannelMessage> listMessages(Integer userId, Integer channelId, Pageable pageable);

    /**
     * Whether {@code userId} is a member (any role) of the group owning {@code channelId} —
     * a pure boolean check, unlike {@link #listMessages}/{@link #postMessage} which throw on
     * failure. Used by {@code api}'s STOMP layer to authorize a channel-topic subscription before
     * the broker admits it, since the simple broker itself has no per-destination ACL.
     *
     * @return {@code false} for a nonexistent channel too — same as "not a member"
     */
    boolean isChannelMember(Integer userId, Integer channelId);
}
