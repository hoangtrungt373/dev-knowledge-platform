package com.ttg.devknowledgeplatform.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.infra.security.RsaKeyUtils;

import java.security.PublicKey;
import java.util.Optional;

/**
 * Verifies (never issues) RS256 JWTs, independently of {@code identity-service}'s
 * {@code JwtTokenProvider} — this service validates tokens itself rather than depending on
 * another module's Java classes, the OAuth2-Resource-Server-style pattern chosen for the
 * standalone extraction (see the {@code project-ecommerce-service-module} memory).
 *
 * <p>Same signing mechanism {@code identity-service}/{@code gateway} already use, so a token
 * issued there verifies here too: RS256, verified with the public key at
 * {@code jwt.public-key-location} (the matching half of the private key {@code identity-service}
 * signs with — this service never holds, and never needs, the private key), claims
 * {@code sub}=email, {@code userUuid}, {@code username}, {@code role}, and (on refresh tokens
 * only) {@code type}. A refresh token is deliberately rejected here — it must only ever be
 * exchanged for a new access token by whichever service issues tokens, never used to authenticate
 * a request directly.
 */
@Component
public class JwtVerifier {

    private final PublicKey publicKey;

    public JwtVerifier(@Value("${jwt.public-key-location}") Resource publicKeyResource) {
        this.publicKey = RsaKeyUtils.readPublicKey(publicKeyResource);
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
                    .verifyWith(publicKey)
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
