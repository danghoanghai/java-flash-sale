package com.flashsale.auth.service;

import com.flashsale.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final long OTP_TTL_MINUTES = 5;
    private static final String OTP_PREFIX = "auth:otp:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Generate a 6-digit OTP, store in Redis, and MOCK-send it via log.
     */
    public void sendOtp(String identifier) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(OTP_PREFIX + identifier, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);

        // ====== MOCK SEND ======
        log.info("========================================");
        log.info("  [MOCK OTP] Sending OTP to: {}", identifier);
        log.info("  [MOCK OTP] Code: {}", otp);
        log.info("  [MOCK OTP] Expires in {} minutes", OTP_TTL_MINUTES);
        log.info("========================================");
    }

    /**
     * Verify the OTP. Consumes it on success (one-time use).
     */
    public void verifyOtp(String identifier, String otp) {
        String key = OTP_PREFIX + identifier;
        Object stored = redisTemplate.opsForValue().get(key);

        if (stored == null) {
            throw new BusinessException("OTP expired or not found. Please request a new one.");
        }
        if (!stored.toString().equals(otp)) {
            throw new BusinessException("Invalid OTP.");
        }

        // Consume the OTP so it can't be reused
        redisTemplate.delete(key);
    }
}