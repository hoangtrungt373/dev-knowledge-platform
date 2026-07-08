package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.friend.FriendRequestResponse;
import com.ttg.devknowledgeplatform.dto.friend.FriendSummaryResponse;
import com.ttg.devknowledgeplatform.dto.friend.UserSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the friend graph: requests, friendships, and blocking.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.api.impl.FriendController}) carries
 * no HTTP annotations. User search lives on {@link UserApi} instead, alongside the other
 * user-profile endpoints.
 */
@RequestMapping("/api/v1/friends")
public interface FriendApi {

    /**
     * Sends a friend request to another user. Auto-accepts into a friendship if that user
     * already has a pending request to the caller.
     *
     * @param userId   authenticated caller's integer primary key, injected from the JWT
     * @param userUuid UUID of the user to friend
     * @return {@code 201} with the resulting request (or accepted request on auto-accept)
     */
    @PostMapping("/requests/{userUuid}")
    ResponseEntity<FriendRequestResponse> sendRequest(
            @CurrentUserId Integer userId, @PathVariable String userUuid);

    /**
     * Accepts a pending request addressed to the caller.
     *
     * @return {@code 200} with the now-accepted request
     */
    @PostMapping("/requests/{requestId}/accept")
    ResponseEntity<FriendRequestResponse> acceptRequest(
            @CurrentUserId Integer userId, @PathVariable Integer requestId);

    /**
     * Rejects a pending request addressed to the caller.
     *
     * @return {@code 200} with the now-rejected request
     */
    @PostMapping("/requests/{requestId}/reject")
    ResponseEntity<FriendRequestResponse> rejectRequest(
            @CurrentUserId Integer userId, @PathVariable Integer requestId);

    /**
     * Cancels a pending request the caller sent.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/requests/{requestId}")
    ResponseEntity<Void> cancelRequest(
            @CurrentUserId Integer userId, @PathVariable Integer requestId);

    /**
     * Returns pending requests addressed to the caller.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping("/requests/incoming")
    ResponseEntity<PagedResponse<FriendRequestResponse>> listIncomingRequests(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);

    /**
     * Returns pending requests the caller has sent.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping("/requests/outgoing")
    ResponseEntity<PagedResponse<FriendRequestResponse>> listOutgoingRequests(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);

    /**
     * Returns the caller's friend list.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping
    ResponseEntity<PagedResponse<FriendSummaryResponse>> listFriends(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);

    /**
     * Removes an existing friendship.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{userUuid}")
    ResponseEntity<Void> unfriend(
            @CurrentUserId Integer userId, @PathVariable String userUuid);

    /**
     * Blocks a user, cascading to remove any existing friendship or pending request between
     * the pair.
     *
     * @return {@code 204 No Content}
     */
    @PostMapping("/blocks/{userUuid}")
    ResponseEntity<Void> block(
            @CurrentUserId Integer userId, @PathVariable String userUuid);

    /**
     * Removes a block the caller previously created. Idempotent.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/blocks/{userUuid}")
    ResponseEntity<Void> unblock(
            @CurrentUserId Integer userId, @PathVariable String userUuid);

    /**
     * Returns users blocked by the caller.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping("/blocks")
    ResponseEntity<PagedResponse<UserSummaryResponse>> listBlockedUsers(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);
}
