package com.flashsale.flashsale.service;

import com.flashsale.flashsale.entity.FlashSale;
import com.flashsale.flashsale.entity.FlashSaleProduct;
import com.flashsale.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.flashsale.repository.FlashSaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockWarmUpService {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("=== Starting Redis stock warm-up ===");

        List<FlashSale> activeSales = flashSaleRepository.findByStatus(1);
        int count = 0;

        for (FlashSale sale : activeSales) {
            long startMillis = sale.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli();
            long endMillis = sale.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli();

            List<FlashSaleProduct> products = flashSaleProductRepository.findByFlashSaleIdAndEnabledTrue(sale.getId());

            for (FlashSaleProduct fsp : products) {
                Long fspId = fsp.getId();

                // Warm up sale time window
                redisTemplate.opsForValue().set("fs:fsp:" + fspId + ":start", String.valueOf(startMillis));
                redisTemplate.opsForValue().set("fs:fsp:" + fspId + ":end", String.valueOf(endMillis));

                // Warm up allocated stock for this flash sale product
                redisTemplate.opsForValue().set("fs:fsp:" + fspId + ":stock",
                        String.valueOf(fsp.getSaleAvailable()));

                // Cache sale price in cents (integer) for Lua script
                long priceCents = fsp.getSalePrice()
                        .multiply(BigDecimal.valueOf(100)).longValueExact();
                redisTemplate.opsForValue().set("fs:fsp:" + fspId + ":price",
                        String.valueOf(priceCents));

                log.info("Warmed fspId={} | productId={} | stock={} | sale=[{}] | window=[{} -> {}]",
                        fspId, fsp.getProductId(), fsp.getSaleAvailable(),
                        sale.getName(), sale.getStartTime(), sale.getEndTime());
                count++;
            }
        }

        log.info("=== Redis warm-up complete: {} flash sale products loaded ===", count);
    }
}
