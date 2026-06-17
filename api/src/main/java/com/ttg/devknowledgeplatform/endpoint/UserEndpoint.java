package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.dto.user.UpdateProfileRequest;
import com.ttg.devknowledgeplatform.mapper.UserMapper;
import com.ttg.devknowledgeplatform.security.service.UserService;
import com.ttg.devknowledgeplatform.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserEndpoint {

    private final UserService userService;
    private final StorageService storageService;
    private final UserMapper userMapper;

    @PutMapping("/me")
    public ResponseEntity<UserInfoResponse> updateProfile(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(
                principal.getEmail(), request.getFirstName(), request.getLastName(), request.getUsername());
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<UserInfoResponse> uploadAvatar(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestParam("file") MultipartFile file) {
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

    @GetMapping("/public/{userUuid}")
    public ResponseEntity<UserInfoResponse> getPublicProfile(@PathVariable String userUuid) {
        User user = userService.findByUserUuidOptional(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }

}
