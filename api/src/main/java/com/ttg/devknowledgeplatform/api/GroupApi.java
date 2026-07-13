package com.ttg.devknowledgeplatform.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ttg.devknowledgeplatform.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.messaging.ChangeRoleRequest;
import com.ttg.devknowledgeplatform.dto.messaging.ChannelMessageResponse;
import com.ttg.devknowledgeplatform.dto.messaging.ChannelResponse;
import com.ttg.devknowledgeplatform.dto.messaging.CreateChannelRequest;
import com.ttg.devknowledgeplatform.dto.messaging.CreateGroupRequest;
import com.ttg.devknowledgeplatform.dto.messaging.GroupMemberResponse;
import com.ttg.devknowledgeplatform.dto.messaging.GroupResponse;
import com.ttg.devknowledgeplatform.dto.messaging.SendMessageRequest;

/**
 * HTTP contract for multi-user group chat: groups, membership/roles, channels, and channel
 * messages.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.api.impl.GroupController}) carries no
 * HTTP annotations. Channel-message endpoints are keyed by {@code channelId} alone, not nested
 * under {@code groupId} — {@code GroupService} resolves the owning group from the channel itself,
 * so a {@code groupId} path segment there would be unvalidated and misleading if it didn't match.
 */
@RequestMapping("/api/v1")
public interface GroupApi {

    /** Creates a group with the caller as its sole member, holding {@code OWNER}. */
    @PostMapping("/groups")
    ResponseEntity<GroupResponse> createGroup(
            @CurrentUserId Integer userId, @Valid @RequestBody CreateGroupRequest request);

    /**
     * Returns groups the caller belongs to.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20)
     */
    @GetMapping("/groups")
    ResponseEntity<PagedResponse<GroupResponse>> listMyGroups(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * Adds a user to the group as a {@code MEMBER}. Open add — no friendship required, only that
     * the caller holds {@code OWNER}/{@code ADMIN}. Idempotent.
     */
    @PostMapping("/groups/{groupId}/members/{userUuid}")
    ResponseEntity<GroupMemberResponse> addMember(
            @CurrentUserId Integer userId, @PathVariable Integer groupId, @PathVariable String userUuid);

    /**
     * Removes a member from the group. The owner can never be removed this way; an {@code ADMIN}
     * can only be removed by the {@code OWNER}.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/groups/{groupId}/members/{userUuid}")
    ResponseEntity<Void> removeMember(
            @CurrentUserId Integer userId, @PathVariable Integer groupId, @PathVariable String userUuid);

    /**
     * Removes the caller's own membership. The owner cannot leave in this MVP.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/groups/{groupId}/members/me")
    ResponseEntity<Void> leaveGroup(@CurrentUserId Integer userId, @PathVariable Integer groupId);

    /**
     * Changes a member's role. Only the {@code OWNER} can call this; ownership itself is not
     * reassignable via this endpoint.
     */
    @PutMapping("/groups/{groupId}/members/{userUuid}/role")
    ResponseEntity<GroupMemberResponse> changeRole(
            @CurrentUserId Integer userId,
            @PathVariable Integer groupId,
            @PathVariable String userUuid,
            @Valid @RequestBody ChangeRoleRequest request);

    /** Creates a channel. Requires the caller to hold {@code OWNER}/{@code ADMIN}. */
    @PostMapping("/groups/{groupId}/channels")
    ResponseEntity<ChannelResponse> createChannel(
            @CurrentUserId Integer userId, @PathVariable Integer groupId, @Valid @RequestBody CreateChannelRequest request);

    /** Returns the channels in a group. Requires the caller to be a member. */
    @GetMapping("/groups/{groupId}/channels")
    ResponseEntity<List<ChannelResponse>> listChannels(@CurrentUserId Integer userId, @PathVariable Integer groupId);

    /**
     * Posts a message to a channel. Requires the caller to be a member (any role) of the
     * channel's group. {@code content}/{@code attachment} may both be present.
     */
    @PostMapping("/channels/{channelId}/messages")
    ResponseEntity<ChannelMessageResponse> postMessage(
            @CurrentUserId Integer userId, @PathVariable Integer channelId, @Valid @RequestBody SendMessageRequest request);

    /**
     * Returns a channel's message history, most recent first.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20)
     */
    @GetMapping("/channels/{channelId}/messages")
    ResponseEntity<PagedResponse<ChannelMessageResponse>> listMessages(
            @CurrentUserId Integer userId,
            @PathVariable Integer channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);
}
