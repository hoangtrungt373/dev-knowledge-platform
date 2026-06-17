package com.ttg.devknowledgeplatform.security.service;

/**
 * Tracks revoked refresh tokens so they cannot be reused after logout or rotation.
 *
 * <p>Entries are stored in Redis with a TTL matching the token's remaining lifetime,
 * so the blacklist never grows unboundedly and no manual cleanup is required.
 */
public interface RefreshTokenBlacklistService {

    /**
     * Marks a refresh token as revoked for the duration of its remaining lifetime.
     *
     * <p>If {@code ttlSeconds} is zero or negative the call is a no-op — an already-expired
     * token cannot be presented successfully, so blacklisting it is unnecessary.
     *
     * @param refreshToken the raw refresh-token string to revoke
     * @param ttlSeconds   how long (in seconds) to keep the entry; must be positive to have effect
     */
    void blacklist(String refreshToken, long ttlSeconds);

    /**
     * Returns {@code true} if the token has been explicitly revoked and is still within its TTL.
     *
     * @param refreshToken the raw refresh-token string to check
     * @return {@code true} if blacklisted, {@code false} if valid or entry has expired
     */
    boolean isBlacklisted(String refreshToken);
}
