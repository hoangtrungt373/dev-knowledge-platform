package com.ttg.devknowledgeplatform.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * This module's own Redis wiring — moved here from {@code gateway}'s {@code RedisCacheConfig}.
 * Only the {@link #bucket4jRedisConnection} bean made the move: that class's other two beans
 * ({@code baseRedisCacheConfiguration}, {@code cacheManager} — the Spring Cache Abstraction
 * machinery, {@code @EnableCaching}) were found to have zero {@code @Cacheable}/{@code @CacheEvict}
 * consumers anywhere in the reactor when this module was extracted, so they were deleted outright
 * rather than moved — dead code left behind by a caching layer that was wired up but never
 * actually used (see {@code docs/CHANGELOG.md}'s {@code [Unreleased]} entry for this extraction).
 */
@Configuration
public class RedisConfig {

    /**
     * Dedicated Redis connection for Bucket4j rate limiting.
     *
     * <p>Bucket4j stores bucket state as binary data, so it requires a
     * {@code StatefulRedisConnection<String, byte[]>} with a mixed codec —
     * string keys for readability and {@code byte[]} values for Bucket4j's
     * internal binary format. Spring's {@code RedisConnectionFactory} only
     * supports {@code String} or {@code Object} values, so this connection
     * is created directly from the underlying Lettuce {@link RedisClient}.
     *
     * <p>Declared as a bean so it is visible, injectable, and easy to mock
     * in tests — rather than being created inline inside {@code ChatRateLimiter}.
     *
     * @param connectionFactory Spring's auto-configured Lettuce connection factory
     * @return a persistent connection reused by {@code ChatRateLimiter} for all rate-limit checks
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(
            LettuceConnectionFactory connectionFactory) {
        Object nativeClient = connectionFactory.getNativeClient();
        if (!(nativeClient instanceof RedisClient redisClient)) {
            throw new IllegalStateException(
                "Bucket4j rate limiting requires a standalone Lettuce RedisClient, got: " +
                (nativeClient == null ? "null" : nativeClient.getClass().getName()) +
                ". Redis Cluster is not supported.");
        }
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }
}
