package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.dto.friend.UserSearchResultResponse;
import com.ttg.devknowledgeplatform.dto.user.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for the user profile API.
 *
 * <p>Covers profile update, avatar upload, and public profile retrieval. The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.UserController}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/users")
public interface UserApi {

    /**
     * Updates the display name and username of the currently authenticated user.
     *
     * @param principal the authenticated OAuth2 user
     * @param request   validated profile update payload
     * @return {@code 200} with the updated user information
     */
    @PutMapping("/me")
    ResponseEntity<UserInfoResponse> updateProfile(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody UpdateProfileRequest request);

    /**
     * Uploads a new avatar image for the currently authenticated user.
     *
     * <p>If a non-URL avatar already exists it is deleted from storage before the new one is saved.
     *
     * @param principal the authenticated OAuth2 user
     * @param file      the image file to upload
     * @return {@code 200} with the updated user information including the new avatar URL
     */
    @PostMapping("/me/avatar")
    ResponseEntity<UserInfoResponse> uploadAvatar(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestParam("file") MultipartFile file);

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
