package com.ttg.devknowledgeplatform.config;

/**
 * Cache name constants for keys defined under {@code cache.ttl.*} in application properties.
 *
 * <p>Use these constants wherever a cache name is referenced in code — in service classes
 * calling {@code CacheTtlProperties.getTtlFor()}, and in {@code @Cacheable} annotations —
 * so that a rename in yml is a single change here rather than a text search across files.
 */
public final class CacheNames {

    public static final String STATE_TOKENS = "state-tokens";

    private CacheNames() {}
}
