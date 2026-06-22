package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.OAuth2Api;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.RegisterRequest;
import com.ttg.devknowledgeplatform.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.dto.auth.ExchangeStateRequest;
import com.ttg.devknowledgeplatform.dto.auth.LoginRequest;
import com.ttg.devknowledgeplatform.dto.auth.LoginResponse;
import com.ttg.devknowledgeplatform.dto.auth.LogoutRequest;
import com.ttg.devknowledgeplatform.dto.auth.RefreshTokenRequest;
import com.ttg.devknowledgeplatform.dto.auth.RegisterResponse;
import com.ttg.devknowledgeplatform.dto.auth.ResendOtpRequest;
import com.ttg.devknowledgeplatform.dto.auth.TokenResponse;
import com.ttg.devknowledgeplatform.dto.auth.VerifyOtpRequest;
import com.ttg.devknowledgeplatform.mapper.UserMapper;
import com.ttg.devknowledgeplatform.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.service.EmailService;
import com.ttg.devknowledgeplatform.service.OtpService;
import com.ttg.devknowledgeplatform.security.service.RefreshTokenBlacklistService;
import com.ttg.devknowledgeplatform.security.service.StateTokenService;
import com.ttg.devknowledgeplatform.security.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Implementation of {@link OAuth2Api}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller implements OAuth2Api {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenBlacklistService blacklistService;
    private final StateTokenService stateTokenService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final UserMapper userMapper;

    @Override
    public void oauth2Authorization(String provider, HttpServletResponse response) throws IOException {
        log.info("Redirecting OAuth2 authorization request for provider: {}", provider);
        response.sendRedirect("/oauth2/authorization/" + provider);
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
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

    @Override
    public ResponseEntity<LoginResponse> register(RegisterRequest request) {
        if (userService.findByEmail(request.getEmail()) != null) {
            throw new ApiException(ErrorCode.USER_EMAIL_ALREADY_EXISTS,
                    "An account with email '" + request.getEmail() + "' already exists");
        }

        User user = userService.registerLocalUser(
                request.getEmail(), request.getFirstName(), request.getLastName(), request.getPassword());

        try {
            String otp = otpService.generateAndStore(request.getEmail());
            emailService.sendOtpEmail(request.getEmail(), otp);
            log.info("Verification OTP sent to: {}", request.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send verification OTP to {}: {}", request.getEmail(), e.getMessage());
        }

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

    @Override
    public ResponseEntity<LoginResponse> verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail();

        if (!otpService.hasPendingOtp(email)) {
            throw new ApiException(ErrorCode.AUTH_OTP_EXPIRED, "OTP has expired, please request a new one");
        }
        if (!otpService.verify(email, request.getOtp())) {
            throw new ApiException(ErrorCode.AUTH_OTP_INVALID, "Invalid OTP code");
        }

        User user = userService.enableUser(email);
        userService.updateStatus(user.getId(), UserStatus.ONLINE);

        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        log.info("Email verified and login successful: {} (uuid: {})", user.getEmail(), user.getUserUuid());

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserUuid())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    @Override
    public ResponseEntity<RegisterResponse> resendOtp(ResendOtpRequest request) {
        String email = request.getEmail();
        User user = userService.findByEmail(email);

        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, "No account found for this email");
        }
        if (user.getEmailVerified()) {
            throw new ApiException(ErrorCode.AUTH_OTP_EMAIL_NOT_PENDING, "Email is already verified");
        }

        String otp = otpService.generateAndStore(email);
        emailService.sendOtpEmail(email, otp);
        log.info("OTP resent to: {}", email);

        return ResponseEntity.ok(new RegisterResponse(email, "A new verification code has been sent to your email"));
    }

    @Override
    public ResponseEntity<LoginResponse> exchangeState(ExchangeStateRequest request) {
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

    @Override
    public ResponseEntity<UserInfoResponse> getCurrentUser(CustomOAuth2User principal) {
        User user = userService.findByEmail(principal.getEmail());
        if (user == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found");
        }
        return ResponseEntity.ok(userMapper.toUserInfo(user));
    }

    @Override
    public ResponseEntity<Void> logout(LogoutRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            long ttl = jwtTokenProvider.getRemainingValiditySeconds(request.getRefreshToken());
            blacklistService.blacklist(request.getRefreshToken(), ttl);
            log.info("Refresh token blacklisted on logout");
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<TokenResponse> refreshToken(RefreshTokenRequest request) {
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
}
