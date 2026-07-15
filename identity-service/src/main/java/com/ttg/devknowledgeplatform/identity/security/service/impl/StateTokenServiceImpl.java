package com.ttg.devknowledgeplatform.identity.security.service.impl;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.infra.config.cache.CacheNames;
import com.ttg.devknowledgeplatform.infra.config.cache.CacheTtlProperties;
import com.ttg.devknowledgeplatform.identity.security.service.StateTokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StateTokenServiceImpl implements StateTokenService {

    private static final String KEY_PREFIX = "oauth2:state:";
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheTtlProperties cacheTtlProperties;

    @Override
    public Map<String, String> storeTokenData(String stateToken, Map<String, String> tokenData) {
        Duration ttl = cacheTtlProperties.getTtlFor(CacheNames.STATE_TOKENS);
        try {
            String json = objectMapper.writeValueAsString(tokenData);
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + stateToken, json, ttl);
            log.debug("Stored state token in Redis: {} (ttl={})", stateToken, ttl);
            return tokenData;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize state token data", e);
        }
    }

    @Override
    public Map<String, String> getTokenData(String stateToken) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + stateToken);
        if (json == null) {
            log.debug("State token not found in Redis: {}", stateToken);
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize state token data for state: {}", stateToken, e);
            return null;
        }
    }

    @Override
    public void deleteTokenData(String stateToken) {
        stringRedisTemplate.delete(KEY_PREFIX + stateToken);
        log.debug("Deleted state token from Redis: {}", stateToken);
    }
}
