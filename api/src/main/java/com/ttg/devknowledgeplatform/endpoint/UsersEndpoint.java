package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.endpoint.OAuth2Endpoint.UserInfo;
import com.ttg.devknowledgeplatform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersEndpoint {

    private final UserService userService;

    /** Public profile lookup — available without authentication. */
    @GetMapping("/public/{userUuid}")
    public ResponseEntity<UserInfo> getPublicProfile(@PathVariable String userUuid) {
        User user = userService.findByUserUuidOptional(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
        return ResponseEntity.ok(buildPublicUserInfo(user));
    }

    private UserInfo buildPublicUserInfo(User user) {
        return UserInfo.builder()
                .id(user.getUserUuid())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePicture(user.getProfilePicture())
                .provider(user.getProvider().name())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .status(user.getStatus().name())
                .createdAt(user.getDteCreation())
                .lastModified(user.getDteLastModification())
                .build();
    }
}
