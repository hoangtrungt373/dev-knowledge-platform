package com.ttg.devknowledgeplatform.identity.security.service;

import java.util.Map;
import java.util.UUID;

/**
 * Manages short-lived state tokens used in the OAuth2 authorisation-code flow.
 *
 * <p>During login the frontend generates a {@code state} parameter and stores arbitrary
 * key/value pairs (e.g. redirect URL, PKCE verifier) against it. After the provider
 * redirects back, the backend retrieves and deletes the data in one logical exchange,
 * preventing replay attacks and CSRF.
 *
 * <p>The current implementation persists tokens in Redis with a configurable TTL.
 */
public interface StateTokenService {

    /**
     * Persists arbitrary data associated with a state token.
     *
     * <p>The TTL is read from {@code cache.ttl.state-tokens} in application properties.
     *
     * @param stateToken the opaque state identifier, typically from {@link #generateStateToken()}
     * @param tokenData  key/value pairs to store (e.g. {@code redirectUri}, {@code provider})
     * @return the same {@code tokenData} map, for call-site convenience
     */
    Map<String, String> storeTokenData(String stateToken, Map<String, String> tokenData);

    /**
     * Retrieves the data associated with a state token without removing it.
     *
     * @param stateToken the opaque state identifier
     * @return the stored key/value pairs, or {@code null} if the token is unknown or expired
     */
    Map<String, String> getTokenData(String stateToken);

    /**
     * Removes the state token and its associated data.
     *
     * <p>Should be called immediately after a successful exchange so the token cannot be reused.
     *
     * @param stateToken the opaque state identifier to delete
     */
    void deleteTokenData(String stateToken);

    /**
     * Generates a new cryptographically random state token.
     *
     * @return a UUID string suitable for use as the OAuth2 {@code state} parameter
     */
    default String generateStateToken() {
        return UUID.randomUUID().toString();
    }
}
