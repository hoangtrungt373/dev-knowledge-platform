package com.ttg.devknowledgeplatform.endpoint;

import java.io.IOException;

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
import com.ttg.devknowledgeplatform.common.enums.UserRole;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
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
    
    /**
     * Redirect endpoint for OAuth2 authorization initiation.
     * 
     * <h3>Purpose:</h3>
     * Provides a consistent API path for frontend to initiate OAuth2 login,
     * then redirects to Spring Security's internal OAuth2 endpoint.
     * 
     * <h3>Flow:</h3>
     * <ol>
     *   <li>Frontend calls: GET /api/v1/auth/oauth2/authorization/google</li>
     *   <li>This endpoint redirects to: GET /oauth2/authorization/google</li>
     *   <li>Spring Security handles: Builds authorization URL and redirects to Google</li>
     * </ol>
     * 
     * <h3>Why This Endpoint Exists:</h3>
     * <ul>
     *   <li>Frontend uses consistent API path: /api/v1/auth/...</li>
     *   <li>Spring Security uses internal path: /oauth2/authorization/...</li>
     *   <li>This endpoint bridges the gap (no CORS check - it's navigation)</li>
     * </ul>
     * 
     * <h3>Note:</h3>
     * This is a browser navigation (window.location.href), not a JavaScript API call,
     * so CORS does not apply here. The browser automatically follows the redirect.
     * 
     * @param provider OAuth2 provider name (google, facebook)
     * @param response HTTP response to redirect
     * @throws IOException if redirect fails
     */
    @GetMapping("/oauth2/authorization/{provider}")
    public void oauth2Authorization(@PathVariable String provider, HttpServletResponse response) throws IOException {
        log.info("Redirecting OAuth2 authorization request for provider: {}", provider);
        // Redirect to Spring Security's OAuth2 authorization endpoint
        // Spring Security will then:
        // 1. Build authorization URL with client_id, redirect_uri, scope, etc.
        // 2. Redirect user to OAuth2 provider (Google/Facebook)
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
        
        if (user.getRole() != UserRole.ADMIN) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN, "Admin access required");
        }
        
        userService.updateStatus(user.getId(), UserStatus.ONLINE);
        
        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        
        log.info("Admin login successful: {} (USER_UUID: {})", user.getEmail(), user.getUserUuid());
        
        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user")
    public ResponseEntity<UserInfo> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.AUTH_UNAUTHORIZED, "Authentication required");
        }
        
        User user = userService.findByEmail(principal.getEmail());
        if (user == null) {
            throw new ResourceNotFoundException("User", principal.getEmail());
        }
        
        UserInfo userInfo = buildUserInfo(user);
        
        return ResponseEntity.ok(userInfo);
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // In a stateless JWT implementation, logout is handled on the client side
        // by removing the token from storage
        log.info("User logout requested");
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
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Invalid refresh token");
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Token refresh failed");
        }
    }
    
    @GetMapping("/user/{userUuid}")
    public ResponseEntity<UserInfo> getUserByUuid(@PathVariable String userUuid) {
        User user = userService.findByUserUuidOptional(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User", userUuid));
        
        return ResponseEntity.ok(buildUserInfo(user));
    }
    
    private UserInfo buildUserInfo(User user) {
        return UserInfo.builder()
                .id(user.getUserUuid())
                .userId(user.getId())
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
        private String id;
        private Integer userId;
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
        private String userId;
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
}
