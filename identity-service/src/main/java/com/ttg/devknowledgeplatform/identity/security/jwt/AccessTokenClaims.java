package com.ttg.devknowledgeplatform.identity.security.jwt;

import com.ttg.devknowledgeplatform.common.entity.User;

import java.util.Map;

/**
 * Claim set for a short-lived access token: identifies the user and their role for
 * authenticating a single API request.
 */
public record AccessTokenClaims(String userUuid, String email, String username, String role)
        implements TokenClaims {

    /**
     * Builds the access-token claims for a freshly authenticated user.
     *
     * @param user the authenticated user
     * @return the claims to embed in a new access token
     */
    public static AccessTokenClaims from(User user) {
        return new AccessTokenClaims(
                user.getUserUuid(), user.getEmail(), user.getUsername(), "ROLE_" + user.getRole().name());
    }

    @Override
    public Map<String, Object> toClaimsMap() {
        return Map.of(
                CLAIM_USER_UUID, userUuid,
                CLAIM_EMAIL, email,
                CLAIM_USERNAME, username,
                CLAIM_ROLE, role
        );
    }
}
