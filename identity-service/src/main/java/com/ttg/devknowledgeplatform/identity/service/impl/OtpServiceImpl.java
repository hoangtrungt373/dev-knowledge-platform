package com.ttg.devknowledgeplatform.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.identity.service.OtpService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class OtpServiceImpl implements OtpService {

    private static final String KEY_PREFIX = "otp:email:";
    private static final int OTP_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.mail.otp.expiration-minutes:10}")
    private int expirationMinutes;

    @Override
    public String generateAndStore(String email) {
        String otp = String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, OTP_LENGTH)));
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + email, otp, Duration.ofMinutes(expirationMinutes));
        log.debug("OTP generated and stored for email: {} (ttl={}m)", email, expirationMinutes);
        return otp;
    }

    @Override
    public boolean verify(String email, String otp) {
        String stored = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (stored == null) {
            log.debug("OTP not found in Redis for email: {}", email);
            return false;
        }
        if (!stored.equals(otp)) {
            log.debug("OTP mismatch for email: {}", email);
            return false;
        }
        stringRedisTemplate.delete(KEY_PREFIX + email);
        log.debug("OTP verified and deleted for email: {}", email);
        return true;
    }

    @Override
    public boolean hasPendingOtp(String email) {
        return stringRedisTemplate.hasKey(KEY_PREFIX + email);
    }

    @Override
    public void delete(String email) {
        stringRedisTemplate.delete(KEY_PREFIX + email);
    }
}
