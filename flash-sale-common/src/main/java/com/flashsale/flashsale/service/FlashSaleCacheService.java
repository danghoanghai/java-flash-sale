package com.flashsale.flashsale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.flashsale.entity.FlashSale;
import com.flashsale.flashsale.entity.FlashSaleProduct;
import com.flashsale.flashsale.entity.Product;
import com.flashsale.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.flashsale.repository.FlashSaleRepository;
import com.flashsale.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleCacheService {

    static final String CACHE_KEY = "fs:active:items";
    private static final long CACHE_TTL_SECONDS = 60;

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Cached item metadata — everything EXCEPT availableStock (which is live in Redis).
     */
    public record CachedItem(
            Long flashSaleProductId,
            Long productId,
            String productName,
            BigDecimal originalPrice,
            BigDecimal salePrice,
            String flashSaleName,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {}

    /**
     * Refresh active flash sale item cache.
     * Queries DB once, serializes to JSON, stores in Redis with 60s TTL.
     */
    public void refreshCache() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<FlashSale> activeSales = flashSaleRepository.findActiveAt(now);

            List<CachedItem> items = new ArrayList<>();

            for (FlashSale sale : activeSales) {
                List<FlashSaleProduct> fspList =
                        flashSaleProductRepository.findByFlashSaleIdAndEnabledTrue(sale.getId());

                List<Long> productIds = fspList.stream().map(FlashSaleProduct::getProductId).toList();
                Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));

                for (FlashSaleProduct fsp : fspList) {
                    Product product = productMap.get(fsp.getProductId());
                    items.add(new CachedItem(
                            fsp.getId(),
                            fsp.getProductId(),
                            product != null ? product.getName() : "Unknown",
                            product != null ? product.getOriginalPrice() : BigDecimal.ZERO,
                            fsp.getSalePrice(),
                            sale.getName(),
                            sale.getStartTime(),
                            sale.getEndTime()
                    ));
                }
            }

            String json = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("Flash sale cache refreshed: {} items", items.size());
        } catch (Exception e) {
            log.error("Failed to refresh flash sale cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Read cached items from Redis. Returns null on cache miss.
     */
    public List<CachedItem> getCachedItems() {
        Object raw = redisTemplate.opsForValue().get(CACHE_KEY);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CachedItem.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize flash sale cache: {}", e.getMessage());
            return null;
        }
    }
}
