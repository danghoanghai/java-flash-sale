package com.flashsale.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecretKey signingKey;
    private final long expirationMs;
    
    // TẠO SẴN BỘ PARSER DUY NHẤT (SINGLETON)
    private final JwtParser jwtParser; 

    public TokenService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours}") long expirationHours
    ) {
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationHours * 3600 * 1000;
        
        // BUILD ĐÚNG 1 LẦN KHI KHỞI ĐỘNG SPRING BOOT
        this.jwtParser = Jwts.parser().verifyWith(this.signingKey).build(); 
    }

    public String createToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate the JWT. Returns userId or null if invalid/expired/blacklisted.
     */
    public Long resolveToken(String token) {
        try {
            // Check blacklist first (fast Redis lookup)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                return null;
            }

            // TÁI SỬ DỤNG BỘ PARSER CÓ SẴN (Chỉ tốn chưa tới 0.1ms)
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            return Long.valueOf(claims.getSubject());
        } catch (JwtException | NumberFormatException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Blacklist the token in Redis until its natural expiry.
     */
    public boolean invalidate(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", remainingMs, TimeUnit.MILLISECONDS);
            }
            log.debug("JWT blacklisted");
            return true;
        } catch (JwtException e) {
            log.warn("Cannot blacklist invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}