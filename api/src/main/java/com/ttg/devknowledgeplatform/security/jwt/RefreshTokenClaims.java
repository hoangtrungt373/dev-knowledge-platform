package com.ttg.devknowledgeplatform.security.jwt;

import com.ttg.devknowledgeplatform.common.entity.User;

import java.util.Map;

/**
 * Claim set for a long-lived refresh token: carries just enough identity to mint a new
 * access token, plus the {@code type=refresh} marker that keeps it from being accepted
 * as an access token by {@code JwtAuthenticationFilter}.
 */
public record RefreshTokenClaims(String userUuid, String username, String role) implements TokenClaims {

    /**
     * Builds the refresh-token claims for a freshly authenticated user.
     *
     * @param user the authenticated user
     * @return the claims to embed in a new refresh token
     */
    public static RefreshTokenClaims from(User user) {
        return new RefreshTokenClaims(user.getUserUuid(), user.getUsername(), "ROLE_" + user.getRole().name());
    }

    @Override
    public Map<String, Object> toClaimsMap() {
        return Map.of(
                CLAIM_USER_UUID, userUuid,
                CLAIM_USERNAME, username,
                CLAIM_ROLE, role,
                CLAIM_TYPE, TYPE_REFRESH
        );
    }
}
