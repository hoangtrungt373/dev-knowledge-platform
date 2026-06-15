package com.ttg.devknowledgeplatform.security.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStateTokenService implements StateTokenService {

    private static final String KEY_PREFIX = "oauth2:state:";
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> storeTokenData(String stateToken, Map<String, String> tokenData, long expirationSeconds) {
        try {
            String json = objectMapper.writeValueAsString(tokenData);
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + stateToken, json, Duration.ofSeconds(expirationSeconds));
            log.debug("Stored state token in Redis: {} (ttl={}s)", stateToken, expirationSeconds);
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
