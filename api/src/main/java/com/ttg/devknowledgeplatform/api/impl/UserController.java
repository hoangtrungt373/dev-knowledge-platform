package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.UserApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.dto.user.UpdateProfileRequest;
import com.ttg.devknowledgeplatform.mapper.UserMapper;
import com.ttg.devknowledgeplatform.security.service.UserService;
import com.ttg.devknowledgeplatform.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link UserApi}.
 */
@RestController
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
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found");
        }

        String existing = currentUser.getProfilePicture();
        if (existing != null && !existing.startsWith("http")) {
            storageService.delete(existing);
        }

        String objectKey = storageService.uploadImage("avatars/" + currentUser.getUserUuid(), file);
        User updated = userService.updateAvatar(principal.getEmail(), objectKey);
        return ResponseEntity.ok(userMapper.toUserInfo(updated));
    }

    @Override
    public ResponseEntity<UserInfoResponse> getPublicProfile(String userUuid) {
        User user = userService.findByUserUuidOptional(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }
}
