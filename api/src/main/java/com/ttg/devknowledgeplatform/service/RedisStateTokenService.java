package com.ttg.devknowledgeplatform.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStateTokenService implements StateTokenService {

    private static final String CACHE_NAME = "oauth2-state-tokens";
    private static final String KEY_GENERATOR = "stateTokenKeyGenerator";

    @Override
    @CachePut(value = CACHE_NAME, keyGenerator = KEY_GENERATOR)
    public Map<String, String> storeTokenData(String stateToken, Map<String, String> tokenData, long expirationSeconds) {
        log.debug("Storing token data in Redis cache '{}' for state: {}", CACHE_NAME, stateToken);
        return new HashMap<>(tokenData);
    }

    @Override
    @Cacheable(value = CACHE_NAME, keyGenerator = KEY_GENERATOR, unless = "#result == null")
    public Map<String, String> getTokenData(String stateToken) {
        log.debug("Token data not found in Redis cache '{}' for state: {}", CACHE_NAME, stateToken);
        return null;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, keyGenerator = KEY_GENERATOR)
    public void deleteTokenData(String stateToken) {
        log.debug("Deleted token data from Redis cache '{}' for state: {}", CACHE_NAME, stateToken);
    }
}

