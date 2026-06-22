package com.ttg.devknowledgeplatform.config.cache;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Cache Configuration for Spring Cache Abstraction
 * @author ttg
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig {

    /**
     * Base Redis cache configuration used as default for all caches.
     *
     * <p>This configuration provides:
     * <ul>
     *   <li>Default TTL: 5 minutes (can be overridden per-cache)</li>
     *   <li>Key serialization: String (readable in Redis)</li>
     *   <li>Value serialization: JSON (using GenericJackson2JsonRedisSerializer)</li>
     *   <li>Null values: Not cached (prevents cache pollution)</li>
     * </ul>
     *
     * <p>Individual caches can override the TTL via {@code cache.ttl.{cache-name}} property.
     *
     * @return Base Redis cache configuration
     */
    @Bean
    public RedisCacheConfiguration baseRedisCacheConfiguration(
            CacheTtlProperties cacheTtlProperties,
            ObjectMapper objectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTtlProperties.getDefaultTtl())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();
    }

    /**
     * Creates and configures the RedisCacheManager with per-cache TTL settings.
     *
     * <p>This method:
     * <ol>
     *   <li>Reads all cache TTLs from properties via {@code CacheTtlProperties}</li>
     *   <li>Creates a custom configuration for each cache with its specific TTL</li>
     *   <li>Builds the RedisCacheManager with these configurations</li>
     * </ol>
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration baseConfig,
            CacheTtlProperties cacheTtlProperties) {

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheTtlProperties.getTtl().forEach((cacheName, ttl) -> {
            cacheConfigurations.put(cacheName, baseConfig.entryTtl(ttl));
            log.info("Configured cache '{}' with TTL: {}", cacheName, ttl);
        });

        if (cacheConfigurations.isEmpty()) {
            log.warn("No cache TTLs configured under cache.ttl.*; all caches use default TTL: {}", cacheTtlProperties.getDefaultTtl());
        } else {
            log.info("Configured {} cache(s) with custom TTLs", cacheConfigurations.size());
        }

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

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
