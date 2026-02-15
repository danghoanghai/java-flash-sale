package com.flashsale.flashsale.service;

import com.flashsale.common.exception.BusinessException;
import com.flashsale.flashsale.dto.FlashSaleItemResponse;
import com.flashsale.flashsale.event.PurchaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        return cached.stream().map(item -> FlashSaleItemResponse.builder()
                .flashSaleProductId(item.flashSaleProductId())
                .productId(item.productId())
                .productName(item.productName())
                .originalPrice(item.originalPrice())
                .salePrice(item.salePrice())
                .flashSaleName(item.flashSaleName())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .availableStock(getLiveStock(item.flashSaleProductId()))
                .build()
        ).toList();
    }

    private Integer getLiveStock(Long fspId) {
        Object redisStock = redisTemplate.opsForValue().get("fs:fsp:" + fspId + ":stock");
        if (redisStock != null) {
            return Integer.parseInt(redisStock.toString());
        }
        return 0;
    }

    /**
     * Execute the atomic flash-sale purchase via Redis Lua script.
     * No DB transaction is involved in the hot path — only Redis.
     */
    public String attemptPurchase(Long userId, Long flashSaleProductId) {
        String today = LocalDate.now().format(DATE_FMT);

        // Build the 4 KEYS the Lua script expects
        List<String> keys = List.of(
                "fs:fsp:" + flashSaleProductId + ":stock",
                "fs:user:" + userId + ":daily:" + today,
                "fs:fsp:" + flashSaleProductId + ":start",
                "fs:fsp:" + flashSaleProductId + ":end"
        );

        long nowMillis = System.currentTimeMillis();

        Long result = redisTemplate.execute(flashSaleLuaScript, keys, String.valueOf(nowMillis));

        if (result == null) {
            throw new BusinessException(500, "Flash sale service unavailable");
        }

        return switch (result.intValue()) {
            case 1 -> {
                String orderNo = generateOrderNo(userId, flashSaleProductId);
                log.info("Purchase SUCCESS | user={} fspId={} orderNo={}", userId, flashSaleProductId, orderNo);

                BigDecimal price = readCachedPrice(flashSaleProductId);

                // Fire async persistence event (non-blocking)
                eventPublisher.publishEvent(new PurchaseEvent(this, userId, flashSaleProductId, price, orderNo));

                yield orderNo;
            }
            case -1 -> throw new BusinessException("Flash sale has not started yet");
            case -2 -> throw new BusinessException("Flash sale has already ended");
            case -3 -> throw new BusinessException("You have already purchased a flash sale product today");
            case -4 -> throw new BusinessException("Item is sold out");
            default -> throw new BusinessException(500, "Unexpected result from flash sale script: " + result);
        };
    }

    private BigDecimal readCachedPrice(Long fspId) {
        Object priceObj = redisTemplate.opsForValue().get("fs:fsp:" + fspId + ":price");
        if (priceObj == null) {
            log.warn("Price not found in cache for fspId={}, defaulting to 0", fspId);
            return BigDecimal.ZERO;
        }
        return new BigDecimal(priceObj.toString());
    }

    private String generateOrderNo(Long userId, Long fspId) {
        String datePart = LocalDate.now().format(DATE_FMT);
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "FS-" + datePart + "-" + userId + "-" + fspId + "-" + randomPart;
    }
}
