package com.ttg.devknowledgeplatform.config.chat;

import com.ttg.devknowledgeplatform.common.exception.RateLimitExceededException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user token-bucket rate limiter for the AI chat endpoint, backed by Redis.
 *
 * <p>Each authenticated user gets their own {@link Bucket} stored in Redis, enforcing
 * two simultaneous {@link Bandwidth} limits from {@link RateLimitProperties}:
 * <ul>
 *   <li>A per-minute limit — prevents burst abuse.</li>
 *   <li>A per-hour limit — caps total OpenAI cost per user per hour.</li>
 * </ul>
 *
 * <p>Using Redis as the backend (via {@link LettuceBasedProxyManager}) means bucket
 * state is shared across all application instances, so horizontal scaling does not
 * allow users to bypass limits by hitting different nodes.
 *
 * <p>Bucket keys are stored under the {@code rate:chat:{userId}} namespace and expire
 * automatically after {@code app.ai.rate-limit.bucket-expiration} of inactivity.
 */
@Component
@Slf4j
public class ChatRateLimiter {

    private static final String KEY_PREFIX = "rate:chat:";

    private final LettuceBasedProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfig;

    public ChatRateLimiter(StatefulRedisConnection<String, byte[]> redisConnection,
                           RateLimitProperties properties) {
        ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                .withExpirationAfterWriteStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(properties.getBucketExpiration()));

        this.proxyManager = LettuceBasedProxyManager
                .builderFor(redisConnection)
                .withClientSideConfig(clientSideConfig)
                .build();

        this.bucketConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getRequestsPerMinute())
                        .refillGreedy(properties.getRequestsPerMinute(), Duration.ofMinutes(1))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(properties.getRequestsPerHour())
                        .refillGreedy(properties.getRequestsPerHour(), Duration.ofHours(1))
                        .build())
                .build();
    }

    /**
     * Attempts to consume one token from the given user's Redis-backed bucket.
     *
     * @param userId the authenticated user's identifier (from {@code Authentication.getName()})
     * @throws RateLimitExceededException if either the per-minute or per-hour limit is exhausted
     */
    public void consume(String userId) {
        Bucket bucket = proxyManager.builder().build(KEY_PREFIX + userId, bucketConfig);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user={}", userId);
            throw new RateLimitExceededException();
        }
    }
}
