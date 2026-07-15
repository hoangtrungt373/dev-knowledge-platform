package com.ttg.devknowledgeplatform.identity.security.jwt;

import io.jsonwebtoken.Claims;

import java.util.Map;

/**
 * The typed claim set embedded in a signed JWT issued by
 * {@link com.ttg.devknowledgeplatform.identity.security.JwtTokenProvider}.
 *
 * <p>Sealed to exactly the two token shapes the application issues today
 * ({@link AccessTokenClaims}, {@link RefreshTokenClaims}) so that every claim key is spelled out
 * in exactly one place instead of being repeated as string literals at every call site that
 * builds or reads a token — the exact pattern that previously let a claim get renamed in one
 * spot and missed in another. Adding a third token shape later is then a compile error at every
 * exhaustive {@code switch} over this type, not a silently-unhandled case.
 */
public sealed interface TokenClaims permits AccessTokenClaims, RefreshTokenClaims {

    /** Claim key holding the user's public UUID — never the internal numeric primary key. */
    String CLAIM_USER_UUID = "userUuid";

    /** Claim key holding the user's email address. Present on access tokens only. */
    String CLAIM_EMAIL = "email";

    /** Claim key holding the user's display username. */
    String CLAIM_USERNAME = "username";

    /** Claim key holding the Spring Security role, e.g. {@code ROLE_USER}. */
    String CLAIM_ROLE = "role";

    /** Claim key present only on refresh tokens, distinguishing them from access tokens. */
    String CLAIM_TYPE = "type";

    /** {@link #CLAIM_TYPE} value marking a token as a refresh token. */
    String TYPE_REFRESH = "refresh";

    /**
     * @return the authenticated user's public UUID
     */
    String userUuid();

    /**
     * @return the authenticated user's display username
     */
    String username();

    /**
     * @return the authenticated user's Spring Security role, e.g. {@code ROLE_USER}
     */
    String role();

    /**
     * Serializes this claim set to the flat {@code Map<String, Object>} shape the JJWT builder
     * API requires.
     *
     * @return the claims ready to pass to {@code Jwts.builder().claims(...)}
     */
    Map<String, Object> toClaimsMap();

    /**
     * Reconstructs the typed claim set from a parsed, already signature-verified {@link Claims}.
     *
     * <p>Dispatches on {@link #CLAIM_TYPE}: present and equal to {@link #TYPE_REFRESH} yields a
     * {@link RefreshTokenClaims}, anything else yields an {@link AccessTokenClaims}.
     *
     * @param claims the verified claims payload from a parsed JWT
     * @return the typed claim set matching the token's actual shape
     */
    static TokenClaims parse(Claims claims) {
        String userUuid = claims.get(CLAIM_USER_UUID, String.class);
        String username = claims.get(CLAIM_USERNAME, String.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        String type = claims.get(CLAIM_TYPE, String.class);

        if (TYPE_REFRESH.equals(type)) {
            return new RefreshTokenClaims(userUuid, username, role);
        }
        String email = claims.get(CLAIM_EMAIL, String.class);
        return new AccessTokenClaims(userUuid, email, username, role);
    }
}
