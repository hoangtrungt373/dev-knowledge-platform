package com.ttg.devknowledgeplatform.social.api.impl;

import java.util.Set;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.social.api.FriendApi;
import com.ttg.devknowledgeplatform.social.dto.friend.FriendRequestResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.FriendSummaryResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.UserSummaryResponse;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.mapper.FriendMapper;
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

    @Override
    public ResponseEntity<FriendRequestResponse> sendRequest(Integer userId, String addresseeUuid) {
        FriendRequest request = friendService.sendRequest(userId, addresseeUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<FriendRequestResponse> acceptRequest(Integer userId, Integer requestId) {
        FriendRequest request = friendService.acceptRequest(requestId, userId);
        return ResponseEntity.ok(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<FriendRequestResponse> rejectRequest(Integer userId, Integer requestId) {
        FriendRequest request = friendService.rejectRequest(requestId, userId);
        return ResponseEntity.ok(friendMapper.toFriendRequestResponse(request));
    }

    @Override
    public ResponseEntity<Void> cancelRequest(Integer userId, Integer requestId) {
        friendService.cancelRequest(requestId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PagedResponse<FriendRequestResponse>> listIncomingRequests(
            Integer userId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listIncomingRequests(userId, pageable)
                .map(friendMapper::toFriendRequestResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<PagedResponse<FriendRequestResponse>> listOutgoingRequests(
            Integer userId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listOutgoingRequests(userId, pageable)
                .map(friendMapper::toFriendRequestResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<PagedResponse<FriendSummaryResponse>> listFriends(
            Integer userId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listFriends(userId, pageable)
                .map(friendship -> friendMapper.toFriendSummary(friendship, userId));
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<Void> unfriend(Integer userId, String userUuid) {
        friendService.unfriend(userId, userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> block(Integer userId, String userUuid) {
        friendService.block(userId, userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unblock(Integer userId, String userUuid) {
        friendService.unblock(userId, userUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PagedResponse<UserSummaryResponse>> listBlockedUsers(
            Integer userId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = friendService.listBlockedUsers(userId, pageable)
                .map(friendMapper::toUserSummary);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
