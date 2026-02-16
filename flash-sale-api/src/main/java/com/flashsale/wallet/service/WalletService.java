package com.flashsale.wallet.service;

import com.flashsale.wallet.entity.Wallet;
import com.flashsale.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String BALANCE_KEY_PREFIX = "fs:user:";
    private static final String BALANCE_KEY_SUFFIX = ":balance";
    private static final long BALANCE_TTL_SECONDS = 86400;

    private final WalletRepository walletRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Ensure user balance exists in Redis (cache-aside).
     * If missing, lazy-load from DB or create wallet with 0 balance.
     * Uses SETNX to avoid overwriting concurrent writes.
     */
    public void ensureBalanceInRedis(Long userId) {
        String key = BALANCE_KEY_PREFIX + userId + BALANCE_KEY_SUFFIX;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return;
        }
        log.info("Get balance for user={}", userId);
        Wallet wallet = walletRepository.findById(userId).orElseGet(() -> {
            Wallet w = Wallet.builder().userId(userId).balance(BigDecimal.ZERO).build();
            return walletRepository.save(w);
        });

        long balanceCents = wallet.getBalance()
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(balanceCents),
                        BALANCE_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(set)) {
            log.debug("Loaded balance to Redis for userId={} cents={}", userId, balanceCents);
        }
    }
}
