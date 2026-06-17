package com.ttg.devknowledgeplatform.security.service.impl;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.ttg.devknowledgeplatform.security.service.RefreshTokenBlacklistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenBlacklistServiceImpl implements RefreshTokenBlacklistService {

    private static final String KEY_PREFIX = "rt:bl:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void blacklist(String refreshToken, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, "1", Duration.ofSeconds(ttlSeconds));
        log.debug("Refresh token blacklisted for {} seconds", ttlSeconds);
    }

    @Override
    public boolean isBlacklisted(String refreshToken) {
        return stringRedisTemplate.hasKey(KEY_PREFIX + refreshToken);
    }
}
