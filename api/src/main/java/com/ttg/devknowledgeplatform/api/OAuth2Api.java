package com.ttg.devknowledgeplatform.api;

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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

/**
 * HTTP contract for the authentication and OAuth2 API.
 *
 * <p>Covers local login/register/OTP flows, OAuth2 state-token exchange, JWT refresh,
 * logout, and current-user retrieval. The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.OAuth2Controller}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/auth")
public interface OAuth2Api {

    /**
     * Redirects the client to the OAuth2 authorisation endpoint for the given provider.
     *
     * @param provider  the OAuth2 provider name (e.g. {@code google})
     * @param response  the HTTP response used to issue the redirect
     * @throws IOException if the redirect cannot be written
     */
    @GetMapping("/oauth2/authorization/{provider}")
    void oauth2Authorization(@PathVariable String provider, HttpServletResponse response) throws IOException;

    /**
     * Authenticates a user with email and password.
     *
     * @param request validated login payload
     * @return {@code 200} with access token, refresh token, and user information
     */
    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request);

    /**
     * Registers a new local user and sends an OTP verification email.
     *
     * @param request validated registration payload
     * @return {@code 201} with access token, refresh token, and user information
     */
    @PostMapping("/register")
    ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request);

    /**
     * Verifies the email OTP and activates the user account.
     *
     * @param request validated OTP verification payload
     * @return {@code 200} with access token, refresh token, and user information
     */
    @PostMapping("/verify-otp")
    ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request);

    /**
     * Resends an OTP verification email to an unverified account.
     *
     * @param request validated resend-OTP payload containing the email address
     * @return {@code 200} with a confirmation message
     */
    @PostMapping("/resend-otp")
    ResponseEntity<RegisterResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request);

    /**
     * Exchanges a short-lived OAuth2 state token for JWT access and refresh tokens.
     *
     * @param request validated state-token exchange payload
     * @return {@code 200} with access token, refresh token, and user information
     */
    @PostMapping("/exchange-state")
    ResponseEntity<LoginResponse> exchangeState(@Valid @RequestBody ExchangeStateRequest request);

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param principal the authenticated OAuth2 user
     * @return {@code 200} with user information
     */
    @GetMapping("/user")
    ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User principal);

    /**
     * Invalidates the provided refresh token by adding it to the blacklist.
     *
     * @param request optional logout payload containing the refresh token to blacklist
     * @return {@code 200}
     */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request);

    /**
     * Issues a new access token using a valid refresh token.
     *
     * @param request payload containing the refresh token
     * @return {@code 200} with the new access token
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request);
}
