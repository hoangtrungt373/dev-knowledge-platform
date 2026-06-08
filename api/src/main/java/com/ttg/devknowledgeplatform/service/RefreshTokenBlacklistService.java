package com.ttg.devknowledgeplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenBlacklistService {

    private static final String KEY_PREFIX = "rt:bl:";

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklist(String refreshToken, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, "1", Duration.ofSeconds(ttlSeconds));
        log.debug("Refresh token blacklisted for {} seconds", ttlSeconds);
    }

    public boolean isBlacklisted(String refreshToken) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + refreshToken));
    }
}
