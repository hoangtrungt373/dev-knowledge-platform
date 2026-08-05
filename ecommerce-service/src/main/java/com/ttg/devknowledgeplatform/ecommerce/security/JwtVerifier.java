package com.ttg.devknowledgeplatform.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Verifies (never issues) HS512 JWTs, independently of {@code identity-service}'s
 * {@code JwtTokenProvider} — this service validates tokens itself rather than depending on
 * another module's Java classes, the OAuth2-Resource-Server-style pattern chosen for the
 * standalone extraction (see the {@code project-ecommerce-service-module} memory).
 *
 * <p>Same signing mechanism {@code identity-service}/{@code gateway} already use, so a token
 * issued there verifies here too: HS512, key derived from the shared {@code jwt.secret}
 * (must be the same secret value in both services' config), claims {@code sub}=email,
 * {@code userUuid}, {@code username}, {@code role}, and (on refresh tokens only) {@code type}.
 * A refresh token is deliberately rejected here — it must only ever be exchanged for a new
 * access token by whichever service issues tokens, never used to authenticate a request
 * directly.
 */
@Component
public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Verifies {@code token}'s signature and expiration, and extracts its claims.
     *
     * @param token the raw JWT (no {@code "Bearer "} prefix)
     * @return the verified claims, or empty if the token is missing/malformed/expired/a refresh token
     */
    public Optional<VerifiedToken> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if ("refresh".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }

            return Optional.of(new VerifiedToken(
                    claims.getSubject(),
                    claims.get("userUuid", String.class),
                    claims.get("username", String.class),
                    claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The subset of an access token's claims this service actually needs. */
    public record VerifiedToken(String email, String userUuid, String username, String role) {
    }
}
