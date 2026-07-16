package com.ttg.devknowledgeplatform.identity.api.impl;

import com.ttg.devknowledgeplatform.identity.api.UserApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.identity.dto.user.UpdateProfileRequest;
import com.ttg.devknowledgeplatform.identity.mapper.UserMapper;
import com.ttg.devknowledgeplatform.identity.security.service.UserService;
import com.ttg.devknowledgeplatform.infra.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link UserApi}.
 *
 * <p>Bean explicitly named {@code identityUserController} — {@code social-service} has its own,
 * unrelated {@code UserController} (different {@code UserApi}, different package) fronting the
 * relationship-enriched {@code /api/v1/users} endpoints. Spring's default bean name is the
 * decapitalized simple class name only, ignoring package, so both would otherwise collide as
 * {@code userController} once a single component scan (from {@code gateway}'s
 * {@code @SpringBootApplication}) covers every feature module.
 */
@RestController("identityUserController")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;
    private final StorageService storageService;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserInfoResponse> updateProfile(
            CustomOAuth2User principal, UpdateProfileRequest request) {
        User user = userService.updateProfile(
                principal.getEmail(), request.getFirstName(), request.getLastName(), request.getUsername());
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }

    @Override
    public ResponseEntity<UserInfoResponse> uploadAvatar(CustomOAuth2User principal, MultipartFile file) {
        User currentUser = userService.findByEmail(principal.getEmail());
        if (currentUser == null) {
            throw new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND);
        }

        String existing = currentUser.getProfilePicture();
        if (existing != null && !existing.startsWith("http")) {
            storageService.delete(existing);
        }

        String objectKey = storageService.uploadImage("avatars/" + currentUser.getUserUuid(), file);
        User updated = userService.updateAvatar(principal.getEmail(), objectKey);
        return ResponseEntity.ok(userMapper.toUserInfo(updated));
    }
}
