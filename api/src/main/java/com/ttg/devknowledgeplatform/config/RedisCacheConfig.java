package com.ttg.devknowledgeplatform.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
    public RedisCacheConfiguration baseRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // Default TTL for caches without explicit configuration
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }

    /**
     * Configuration properties for cache TTL settings.
     * 
     * <p>This class automatically binds all cache TTLs from {@code cache.ttl.*} properties.
     * To add a new cache, simply add its TTL to the properties file:
     * 
     * <pre>{@code
     * cache:
     *   ttl:
     *     cache-name-1: PT5M
     *     cache-name-2: PT30M
     * }</pre>
     * 
     * <p>The format is ISO-8601 duration (e.g., PT5M = 5 minutes, PT1H = 1 hour).
     */
    @ConfigurationProperties(prefix = "cache.ttl")
    public static class CacheTtlProperties {
        private Map<String, Duration> ttl = new HashMap<>();

        public Map<String, Duration> getTtl() {
            return ttl;
        }

        public void setTtl(Map<String, Duration> ttl) {
            this.ttl = ttl;
        }
    }

    /**
     * Creates the cache TTL properties bean.
     * 
     * @return CacheTtlProperties instance with all cache TTLs from properties
     */
    @Bean
    public CacheTtlProperties cacheTtlProperties() {
        return new CacheTtlProperties();
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
        
        // Build per-cache configurations with custom TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        Map<String, Duration> ttlMap = cacheTtlProperties.getTtl();
        
        for (Map.Entry<String, Duration> entry : ttlMap.entrySet()) {
            String cacheName = entry.getKey();
            Duration ttl = entry.getValue();
            
            if (ttl != null) {
                RedisCacheConfiguration cacheConfig = baseConfig.entryTtl(ttl);
                cacheConfigurations.put(cacheName, cacheConfig);
                log.info("Configured cache '{}' with TTL: {}", cacheName, ttl);
            } else {
                log.warn("Cache '{}' has null TTL, using default TTL", cacheName);
            }
        }
        
        // If no caches configured, log a warning
        if (cacheConfigurations.isEmpty()) {
            log.warn("No cache TTLs configured in cache.ttl.* properties. Using default TTL (5 minutes) for all caches.");
        } else {
            log.info("Configured {} cache(s) with custom TTLs", cacheConfigurations.size());
        }
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Custom key generator for OAuth2 state token caching.
     * 
     * <p>Generates cache keys in the format: {@code "oauth2:state:{stateToken}"}
     * 
     * <p><strong>Usage:</strong>
     * <pre>{@code
     * @Cacheable(value = "oauth2-state-tokens", keyGenerator = "stateTokenKeyGenerator")
     * public Map<String, String> getTokenData(String stateToken) {
     *     // Method body
     * }
     * }</pre>
     */
    @Bean("stateTokenKeyGenerator")
    public KeyGenerator stateTokenKeyGenerator() {
        return (target, method, params) -> {
            // First parameter should be the state token
            if (params.length > 0) {
                String stateToken = String.valueOf(params[0]).trim();
                return "oauth2:state:" + stateToken;
            }
            // Fallback: use method name and all parameters
            return "oauth2:state:" + method.getName();
        };
    }
}
