package com.flashsale.flashsale.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.flashsale.dto.FlashSaleItemResponse;
import com.flashsale.flashsale.event.PurchaseEvent;
import com.flashsale.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> flashSaleLuaScript;
    private final ApplicationEventPublisher eventPublisher;
    private final FlashSaleCacheService flashSaleCacheService;
    private final WalletService walletService;

    /**
     * Get all flash sale items that are active right now.
     * Reads item metadata from Redis cache (populated by background job),
     * then merges with live stock from Redis.
     * Falls back to DB query if cache is empty.
     */
    public List<FlashSaleItemResponse> getActiveFlashSales() {
        List<FlashSaleCacheService.CachedItem> cached = flashSaleCacheService.getCachedItems();

        if (cached == null) {
            log.warn("Flash sale cache miss — triggering refresh");
            flashSaleCacheService.refreshCache();
            cached = flashSaleCacheService.getCachedItems();
        }

        if (cached == null || cached.isEmpty()) {
            return List.of();
        }

        List<String> stockKeys = cached.stream()
                .map(item -> "fs:fsp:" + item.flashSaleProductId() + ":stock")
                .toList();
        List<Object> redisStocks = redisTemplate.opsForValue().multiGet(stockKeys);
        List<FlashSaleItemResponse> responses = new ArrayList<>(cached.size());

        for (int i = 0; i < cached.size(); i++) {
            FlashSaleCacheService.CachedItem item = cached.get(i);
            
            Object redisStock = (redisStocks != null && i < redisStocks.size()) ? redisStocks.get(i) : null;
            Integer availableStock = (redisStock != null) ? Integer.parseInt(redisStock.toString()) : 0;

            responses.add(FlashSaleItemResponse.builder()
                    .flashSaleProductId(item.flashSaleProductId())
                    .productId(item.productId())
                    .productName(item.productName())
                    .originalPrice(item.originalPrice())
                    .salePrice(item.salePrice())
                    .flashSaleName(item.flashSaleName())
                    .startTime(item.startTime())
                    .endTime(item.endTime())
                    .availableStock(availableStock)
                    .build());
        }

        return responses;
    }

    /**
     * Execute the atomic flash-sale purchase via Redis Lua script.
     * No DB transaction is involved in the hot path — only Redis.
     *
     * Flow:
     * 1. Ensure user balance is cached in Redis (lazy warmup)
     * 2. Pre-check sale time window (soft guard, outside Lua)
     * 3. Execute Lua: atomic balance + stock + daily limit check & mutation
     * 4. Fire async persistence event on success
     */
    public String attemptPurchase(Long userId, Long flashSaleProductId) {
        // 1. Ensure user balance is loaded into Redis
        walletService.ensureBalanceInRedis(userId);

        // 2. Pre-check sale time window (soft guard)
        checkSaleTimeWindow(flashSaleProductId);

        String today = LocalDate.now().format(DATE_FMT);

        // TTL = seconds from now until midnight (UTC) so daily key expires at end of calendar day
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime midnightNext = nowUtc.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        long ttlSeconds = ChronoUnit.SECONDS.between(nowUtc, midnightNext);
        if (ttlSeconds < 1) {
            ttlSeconds = 1;
        }

        // 3. Build KEYS and ARGV for Lua script
        List<String> keys = List.of(
                "fs:fsp:" + flashSaleProductId + ":stock",
                "fs:user:" + userId + ":daily:" + today,
                "fs:fsp:" + flashSaleProductId + ":price",
                "fs:user:" + userId + ":balance"
        );
        List<String> args = List.of(String.valueOf(ttlSeconds));

        Long result = redisTemplate.execute(flashSaleLuaScript, keys, args.toArray());

        if (result == null) {
            throw new BusinessException(500, "Flash sale service unavailable");
        }

        return switch (result.intValue()) {
            case 1 -> {
                String orderNo = generateOrderNo(userId, flashSaleProductId);
                log.info("Purchase SUCCESS | user={} fspId={} orderNo={}", userId, flashSaleProductId, orderNo);

                BigDecimal price = readCachedPrice(flashSaleProductId);

                // Fire async persistence event (non-blocking)
                log.debug("Publishing PurchaseEvent | thread={} orderNo={}", Thread.currentThread().getName(), orderNo);
                eventPublisher.publishEvent(new PurchaseEvent(this, userId, flashSaleProductId, price, orderNo));
                log.debug("PurchaseEvent published (should return immediately) | thread={} orderNo={}", Thread.currentThread().getName(), orderNo);

                yield orderNo;
            }
            case -1 -> throw new BusinessException("Item price not found — sale may not be active");
            case -2 -> throw new BusinessException("Balance not loaded — please try again");
            case -3 -> throw new BusinessException("Insufficient balance");
            case -4 -> throw new BusinessException("You have already purchased this item today");
            case -5 -> throw new BusinessException("Item is sold out");
            default -> throw new BusinessException(500, "Unexpected result from flash sale script: " + result);
        };
    }

    /**
     * Soft pre-check: verify the sale time window before hitting Lua.
     * This is NOT the atomic guard — just an early-exit optimization.
     */
    private void checkSaleTimeWindow(Long flashSaleProductId) {
        Object startObj = redisTemplate.opsForValue().get("fs:fsp:" + flashSaleProductId + ":start");
        Object endObj = redisTemplate.opsForValue().get("fs:fsp:" + flashSaleProductId + ":end");

        if (startObj == null || endObj == null) {
            throw new BusinessException("Flash sale not found or not active");
        }

        long now = System.currentTimeMillis();
        long start = Long.parseLong(startObj.toString());
        long end = Long.parseLong(endObj.toString());

        if (now < start) {
            throw new BusinessException("Flash sale has not started yet");
        }
        if (now > end) {
            throw new BusinessException("Flash sale has already ended");
        }
    }

    /**
     * Read cached price (stored in cents) and convert back to BigDecimal.
     */
    private BigDecimal readCachedPrice(Long fspId) {
        Object priceObj = redisTemplate.opsForValue().get("fs:fsp:" + fspId + ":price");
        if (priceObj == null) {
            log.warn("Price not found in cache for fspId={}, defaulting to 0", fspId);
            return BigDecimal.ZERO;
        }
        long priceCents = Long.parseLong(priceObj.toString());
        return BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String generateOrderNo(Long userId, Long fspId) {
        String datePart = LocalDate.now().format(DATE_FMT);
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "FS-" + datePart + "-" + userId + "-" + fspId + "-" + randomPart;
    }
}
