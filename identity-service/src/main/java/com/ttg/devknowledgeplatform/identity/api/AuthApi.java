package com.ttg.devknowledgeplatform.identity.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterResponse;

import jakarta.validation.Valid;

/**
 * HTTP contract for the authenticated caller's own profile lookup, plus account registration.
 *
 * <p>Named {@code OAuth2Api} until the Keycloak migration — login, OTP verification, token
 * refresh, and logout all moved to Keycloak itself (see {@code docs/CHANGELOG.md}'s Keycloak
 * migration entry). Registration came back afterward with a different implementation: rather than
 * hashing/storing a password locally, {@code register} calls Keycloak's Admin REST API server-side
 * ({@code KeycloakAdminService}), since Keycloak's own token endpoint can only authenticate an
 * existing user, never create one. The implementation
 * ({@link com.ttg.devknowledgeplatform.identity.api.impl.AuthController}) carries no HTTP
 * annotations.
 */
@RequestMapping("/api/v1/auth")
public interface AuthApi {

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param principal the authenticated OAuth2 user
     * @return {@code 200} with user information
     */
    @GetMapping("/user")
    ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal CustomOAuth2User principal);

    /**
     * Creates a new Keycloak user account. Unauthenticated — a brand-new user has no token yet
     * (see {@code SecurityConfig}'s {@code permitAll} rule for this one path).
     *
     * @param request the new account's details
     * @return {@code 201} on success; {@code 409} if the email is already registered
     */
    @PostMapping("/register")
    ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request);

    /**
     * Re-sends Keycloak's email-verification link to the authenticated caller's own account.
     *
     * @param principal the authenticated OAuth2 user
     * @return {@code 204} on success; {@code 409} if the email is already verified
     */
    @PostMapping("/resend-verification-email")
    ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal CustomOAuth2User principal);
}
