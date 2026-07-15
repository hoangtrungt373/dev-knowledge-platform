package com.ttg.devknowledgeplatform.identity.api;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.identity.dto.user.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP contract for the user's own profile API.
 *
 * <p>Covers profile update and avatar upload — the pure "my own profile" operations that only
 * need {@code UserService}/{@code UserMapper}/{@code StorageService}, all reachable from
 * {@code identity-service}. Public profile lookup and user search live on
 * {@code api}'s {@code UserApi} instead, since they need {@code social-service}'s
 * {@code FriendService} for relationship enrichment and {@code identity-service} must not
 * depend on {@code social-service}. The implementation
 * ({@link com.ttg.devknowledgeplatform.identity.api.impl.UserController}) carries no HTTP annotations.
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
}
