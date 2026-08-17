package com.ttg.devknowledgeplatform.identity.api.impl;

import com.ttg.devknowledgeplatform.identity.api.AuthApi;
import com.ttg.devknowledgeplatform.identity.entity.User;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterResponse;
import com.ttg.devknowledgeplatform.identity.exception.IdentityErrorCode;
import com.ttg.devknowledgeplatform.identity.mapper.UserMapper;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakAdminService;
import com.ttg.devknowledgeplatform.identity.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link AuthApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController implements AuthApi {

    private final UserService userService;
    private final UserMapper userMapper;
    private final KeycloakAdminService keycloakAdminService;

    @Override
    public ResponseEntity<UserInfoResponse> getCurrentUser(CustomOAuth2User principal) {
        User user = userService.findByEmail(principal.getEmail());
        if (user == null) {
            throw new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND);
        }
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }

    @Override
    public ResponseEntity<RegisterResponse> register(RegisterRequest request) {
        keycloakAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(request.email(), "Account created successfully"));
    }

    @Override
    public ResponseEntity<Void> resendVerificationEmail(CustomOAuth2User principal) {
        User user = userService.findByEmail(principal.getEmail());
        if (user == null) {
            throw new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(IdentityErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        keycloakAdminService.resendVerificationEmail(user.getKeycloakSubjectId());
        return ResponseEntity.noContent().build();
    }
}
