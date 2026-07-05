package com.ttg.devknowledgeplatform.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.security.jwt.AccessTokenClaims;
import com.ttg.devknowledgeplatform.security.jwt.RefreshTokenClaims;
import com.ttg.devknowledgeplatform.security.jwt.TokenClaims;
import com.ttg.devknowledgeplatform.security.service.RefreshTokenBlacklistService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates, parses, and validates JSON Web Tokens for the application.
 *
 * <p>All tokens are signed with HMAC-SHA-512 using a shared secret configured via
 * {@code jwt.secret}. Two token types are issued, whose claim shapes are defined by
 * {@link TokenClaims}'s two sealed implementations:
 * <ul>
 *   <li><b>Access token</b> ({@link AccessTokenClaims}) — short-lived, carries {@code userUuid},
 *       {@code email}, {@code username}, and {@code role} claims. Used for authenticating API
 *       requests.</li>
 *   <li><b>Refresh token</b> ({@link RefreshTokenClaims}) — longer-lived, carries a
 *       {@code type=refresh} claim to distinguish it from access tokens. Used only to mint new
 *       access tokens via {@link #refreshToken(String)}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final RefreshTokenBlacklistService blacklistService;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    @Value("${jwt.refresh.expiration}")
    private long refreshTokenExpiration;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
    
    /**
     * Generates a short-lived access token for the given user.
     *
     * @param user the authenticated user
     * @return a signed JWT access token
     */
    public String generateToken(User user) {
        return createToken(AccessTokenClaims.from(user).toClaimsMap(), user.getEmail(), jwtExpiration);
    }
    
    /**
     * Generates a long-lived refresh token for the given user.
     *
     * <p>The token contains a {@code type=refresh} claim so it can be distinguished from
     * access tokens and rejected if presented to a protected API endpoint.
     *
     * @param user the authenticated user
     * @return a signed JWT refresh token
     */
    public String generateRefreshToken(User user) {
        return createToken(RefreshTokenClaims.from(user).toClaimsMap(), user.getEmail(), refreshTokenExpiration);
    }
    
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Instant now = Instant.now();
        Instant expiryDate = now.plus(expiration, ChronoUnit.MILLIS);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }
    
    /**
     * Extracts the subject (email address) from a token.
     *
     * @param token a signed JWT
     * @return the email address stored as the JWT subject
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }
    
    /**
     * Extracts the user's public UUID from the {@code userUuid} claim.
     *
     * @param token a signed JWT
     * @return the user UUID string
     */
    public String getUserUuidFromToken(String token) {
        return parseClaims(token).userUuid();
    }

    /**
     * Parses and verifies a token's signature, then reconstructs its typed claim set.
     *
     * <p>Prefer this over repeated {@link #getClaimFromToken} calls when more than one claim is
     * needed — each call re-verifies the HMAC signature, so reading several claims one at a time
     * re-does that work once per claim.
     *
     * @param token a signed JWT
     * @return the token's claims as an {@link AccessTokenClaims} or {@link RefreshTokenClaims}
     */
    public TokenClaims parseClaims(String token) {
        return TokenClaims.parse(getAllClaimsFromToken(token));
    }

    /**
     * Extracts the expiration timestamp from a token.
     *
     * @param token a signed JWT
     * @return the expiration {@link Date}
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }
    
    /**
     * Extracts an arbitrary claim from a token using the supplied resolver function.
     *
     * @param <T>            the claim value type
     * @param token          a signed JWT
     * @param claimsResolver a function that reads the desired value from the full {@link Claims} map
     * @return the resolved claim value
     */
    public <T> T getClaimFromToken(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Returns {@code true} if the token's expiration timestamp is in the past.
     *
     * @param token a signed JWT
     * @return {@code true} if expired
     */
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
    
    /**
     * Validates that a token's subject matches the expected username and has not expired.
     *
     * @param token    a signed JWT
     * @param username the email address expected as the JWT subject
     * @return {@code true} if the token is valid for the given user
     */
    public Boolean validateToken(String token, String username) {
        try {
            final String tokenUsername = getUsernameFromToken(token);
            return (username.equals(tokenUsername) && !isTokenExpired(token));
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Returns how many seconds remain before the token expires.
     *
     * <p>Returns {@code 0} if the token is already expired or if parsing fails,
     * making this safe to use for TTL calculations without additional error handling.
     *
     * @param token a signed JWT
     * @return remaining validity in seconds, never negative
     */
    public long getRemainingValiditySeconds(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Issues a new access token from a valid, non-blacklisted refresh token.
     *
     * <p>The refresh token must have a {@code type=refresh} claim; passing an access token
     * is rejected. The resulting access token reuses the claims from the refresh token
     * (userUuid, email, role, username) and gets a fresh expiration window.
     *
     * @param refreshToken the refresh token string
     * @return a new signed access token
     * @throws IllegalArgumentException if the token has been revoked, is not a refresh token,
     *                                  is expired, or has an invalid signature
     */
    public String refreshToken(String refreshToken) {
        try {
            if (blacklistService.isBlacklisted(refreshToken)) {
                throw new IllegalArgumentException("Refresh token has been revoked");
            }

            Claims claims = getAllClaimsFromToken(refreshToken);
            // Sealed TokenClaims makes this exhaustive at compile time: an AccessTokenClaims
            // presented here is a type mismatch, not just a string comparison that failed.
            if (!(TokenClaims.parse(claims) instanceof RefreshTokenClaims refreshClaims)) {
                throw new IllegalArgumentException("Invalid refresh token");
            }

            String email = claims.getSubject();
            AccessTokenClaims newAccessClaims = new AccessTokenClaims(
                    refreshClaims.userUuid(),
                    email,
                    refreshClaims.username(),
                    refreshClaims.role() != null ? refreshClaims.role() : "ROLE_USER");

            return createToken(newAccessClaims.toClaimsMap(), email, jwtExpiration);

        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid refresh token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid refresh token");
        }
    }
}
