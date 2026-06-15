package com.ttg.devknowledgeplatform.endpoint;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.RegisterRequest;
import com.ttg.devknowledgeplatform.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.security.service.RefreshTokenBlacklistService;
import com.ttg.devknowledgeplatform.security.service.StateTokenService;
import com.ttg.devknowledgeplatform.security.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Endpoint {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenBlacklistService blacklistService;
    private final StateTokenService stateTokenService;

    @GetMapping("/oauth2/authorization/{provider}")
    public void oauth2Authorization(@PathVariable String provider, HttpServletResponse response) throws IOException {
        log.info("Redirecting OAuth2 authorization request for provider: {}", provider);
        response.sendRedirect("/oauth2/authorization/" + provider);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByEmail(request.getEmail());

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(ErrorCode.AUTH_UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.getEnabled()) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN, "Account is disabled");
        }

        userService.updateStatus(user.getId(), UserStatus.ONLINE);

        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("Login successful: {} role={} (uuid: {})", user.getEmail(), user.getRole(), user.getUserUuid());

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.findByEmail(request.getEmail()) != null) {
            throw new ApiException(ErrorCode.USER_EMAIL_ALREADY_EXISTS,
                    "An account with email '" + request.getEmail() + "' already exists");
        }

        User user = userService.registerLocalUser(
                request.getEmail(), request.getFirstName(), request.getLastName(), request.getPassword());

        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("Local registration successful: {} (uuid: {})", user.getEmail(), user.getUserUuid());

        return ResponseEntity.status(HttpStatus.CREATED).body(LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    @PostMapping("/exchange-state")
    public ResponseEntity<LoginResponse> exchangeState(@Valid @RequestBody ExchangeStateRequest request) {
        Map<String, String> tokenData = stateTokenService.getTokenData(request.getStateToken());
        if (tokenData == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "State token not found or expired");
        }
        stateTokenService.deleteTokenData(request.getStateToken());

        log.info("State token exchanged for user: {}", tokenData.get("email"));

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(tokenData.get("accessToken"))
                .refreshToken(tokenData.get("refreshToken"))
                .userId(tokenData.get("userId"))
                .username(tokenData.get("username"))
                .email(tokenData.get("email"))
                .role(tokenData.get("role"))
                .build());
    }

    @GetMapping("/user")
    public ResponseEntity<UserInfo> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.AUTH_UNAUTHORIZED, "Authentication required");
        }
        User user = userService.findByEmail(principal.getEmail());
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found");
        }
        return ResponseEntity.ok(buildUserInfo(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            long ttl = jwtTokenProvider.getRemainingValiditySeconds(request.getRefreshToken());
            blacklistService.blacklist(request.getRefreshToken(), ttl);
            log.info("Refresh token blacklisted on logout");
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_MISSING, "Refresh token is required");
        }
        try {
            String newToken = jwtTokenProvider.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(new TokenResponse(newToken));
        } catch (IllegalArgumentException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token refresh failed");
        }
    }

    private UserInfo buildUserInfo(User user) {
        return UserInfo.builder()
                .id(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
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

    @Data
    @Builder
    public static class UserInfo {
        private String id;         // UUID — the only external user identifier
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String profilePicture;
        private String provider;
        private String role;
        private Boolean emailVerified;
        private String status;
        private java.time.Instant createdAt;
        private java.time.Instant lastModified;
    }

    @Data
    public static class ExchangeStateRequest {
        @NotBlank(message = "State token is required")
        private String stateToken;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        private String email;
        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @Builder
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private String userId;      // UUID
        private String username;
        private String email;
        private String role;
    }

    @Data
    public static class TokenResponse {
        private String accessToken;

        public TokenResponse(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    @Data
    public static class RefreshTokenRequest {
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        private String refreshToken;
    }
}
