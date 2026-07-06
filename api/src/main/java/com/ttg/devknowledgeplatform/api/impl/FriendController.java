package com.ttg.devknowledgeplatform.api.impl;

import java.util.Set;

import com.ttg.devknowledgeplatform.api.FriendApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.friend.FriendRequestResponse;
import com.ttg.devknowledgeplatform.dto.friend.FriendSummaryResponse;
import com.ttg.devknowledgeplatform.dto.friend.UserSummaryResponse;
import com.ttg.devknowledgeplatform.mapper.FriendMapper;
import com.ttg.devknowledgeplatform.security.service.UserService;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.service.FriendService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link FriendApi}.
 */
@RestController
@RequiredArgsConstructor
public class FriendController implements FriendApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final FriendService friendService;
    private final FriendMapper friendMapper;
    private final UserService userService;

    @Override
    public ResponseEntity<FriendRequestResponse> sendRequest(CustomOAuth2User principal, String userUuid) {
        Integer requesterId = currentUserId(principal);
        FriendRequest request = friendService.sendRequest(requesterId, userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<FriendRequestResponse> acceptRequest(CustomOAuth2User principal, Integer requestId) {
        FriendRequest request = friendService.acceptRequest(requestId, currentUserId(principal));
        return ResponseEntity.ok(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<FriendRequestResponse> rejectRequest(CustomOAuth2User principal, Integer requestId) {
        FriendRequest request = friendService.rejectRequest(requestId, currentUserId(principal));
        return ResponseEntity.ok(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<Void> cancelRequest(CustomOAuth2User principal, Integer requestId) {
        friendService.cancelRequest(requestId, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PagedResponse<FriendRequestResponse>> listIncomingRequests(
            CustomOAuth2User principal, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listIncomingRequests(currentUserId(principal), pageable)
                .map(friendMapper::toFriendRequestResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<PagedResponse<FriendRequestResponse>> listOutgoingRequests(
            CustomOAuth2User principal, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listOutgoingRequests(currentUserId(principal), pageable)
                .map(friendMapper::toFriendRequestResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<PagedResponse<FriendSummaryResponse>> listFriends(
            CustomOAuth2User principal, int page, int size, String sortBy, String sortDir) {
        Integer viewerId = currentUserId(principal);
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listFriends(viewerId, pageable)
                .map(friendship -> friendMapper.toFriendSummary(friendship, viewerId));
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<Void> unfriend(CustomOAuth2User principal, String userUuid) {
        friendService.unfriend(currentUserId(principal), userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> block(CustomOAuth2User principal, String userUuid) {
        friendService.block(currentUserId(principal), userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unblock(CustomOAuth2User principal, String userUuid) {
        friendService.unblock(currentUserId(principal), userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PagedResponse<UserSummaryResponse>> listBlockedUsers(
            CustomOAuth2User principal, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listBlockedUsers(currentUserId(principal), pageable)
                .map(friendMapper::toUserSummary);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private Integer currentUserId(CustomOAuth2User principal) {
        User user = userService.resolveCurrentUser(principal);
        return user.getId();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
