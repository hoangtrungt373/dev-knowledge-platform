package com.ttg.devknowledgeplatform.config.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds cache TTL settings from {@code cache.*} properties.
 *
 * <pre>{@code
 * cache:
 *   default-ttl: PT5M
 *   ttl:
 *     cache-name-1: PT10M
 *     cache-name-2: PT30M
 * }</pre>
 */
@ConfigurationProperties(prefix = "cache")
@Getter
@Setter
public class CacheTtlProperties {

    /** Fallback TTL applied to any cache without an explicit entry under {@code cache.ttl}. */
    private Duration defaultTtl = Duration.ofMinutes(5);

    private Map<String, Duration> ttl = new HashMap<>();

    /**
     * Returns the configured TTL for the given cache name, falling back to {@link #defaultTtl}
     * if no explicit entry exists under {@code cache.ttl.{cacheName}}.
     */
    public Duration getTtlFor(String cacheName) {
        return ttl.getOrDefault(cacheName, defaultTtl);
    }
}
