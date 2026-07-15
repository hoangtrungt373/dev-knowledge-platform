package com.ttg.devknowledgeplatform.social.api;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.social.dto.friend.UserSearchResultResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the user-directory API: public profile lookup (with friend-graph
 * enrichment) and user search.
 *
 * <p>Lives here (not `identity-service`) because both methods need `identity-service`'s
 * {@code UserService} for the base lookup <em>and</em> this module's own {@code FriendService} for
 * relationship enrichment — the reverse direction (identity-service depending on social-service)
 * would invert the usual auth-is-foundational hierarchy and mix a social-graph view into an auth
 * module. `identity-service` depends only on {@code common}+{@code infra}, so this module reaching
 * into it (the same direction {@code ai-service} already reaches into {@code content-service}) is
 * safe. The pure profile-mutation endpoints ({@code updateProfile}, {@code uploadAvatar}) live in
 * `identity-service`'s own {@code UserApi} instead, since those only need {@code UserService}. The
 * implementation ({@link com.ttg.devknowledgeplatform.social.api.impl.UserController}) carries no
 * HTTP annotations.
 */
@RequestMapping("/api/v1/users")
public interface UserApi {

    /**
     * Returns the public profile for any user by their UUID.
     *
     * <p>When called by an authenticated caller, the response is enriched with
     * {@code relationshipStatus} and {@code mutualFriendCount}. If the target user has blocked
     * the caller, this returns {@code 404} rather than a distinguishable "blocked" response, to
     * preserve mutual invisibility.
     *
     * @param principal the authenticated caller, or {@code null} for an anonymous view
     * @param userUuid  the user's UUID string
     * @return {@code 200} with the user information, or {@code 404} if not found
     */
    @GetMapping("/public/{userUuid}")
    ResponseEntity<UserInfoResponse> getPublicProfile(
            @AuthenticationPrincipal CustomOAuth2User principal, @PathVariable String userUuid);

    /**
     * Searches users by username/name (fuzzy) or email (exact), excluding the caller and any
     * user blocked in either direction.
     *
     * @param principal the authenticated caller
     * @param q         search text; exact-matched against email, fuzzy-matched against username/name
     * @param page      zero-based page number (default 0)
     * @param size      page size (default 20)
     * @param sortBy    field to sort by; allowed values: {@code id}, {@code username}, {@code dteCreation} (default {@code id})
     * @param sortDir   sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping("/search")
    ResponseEntity<PagedResponse<UserSearchResultResponse>> search(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);
}
