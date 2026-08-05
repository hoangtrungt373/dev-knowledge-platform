package com.ttg.devknowledgeplatform.identity.api;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.dto.UserInfoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the authenticated caller's own profile lookup.
 *
 * <p>Named {@code OAuth2Api} until the Keycloak migration — login, registration, OTP
 * verification, token refresh, and logout all moved to Keycloak itself (see
 * {@code docs/CHANGELOG.md}'s Keycloak migration entry), leaving only this one endpoint. The
 * implementation ({@link com.ttg.devknowledgeplatform.identity.api.impl.AuthController}) carries
 * no HTTP annotations.
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
}
