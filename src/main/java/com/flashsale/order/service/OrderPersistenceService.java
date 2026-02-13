package com.flashsale.order.service;

import com.flashsale.flashsale.entity.FlashSaleProduct;
import com.flashsale.flashsale.entity.Inventory;
import com.flashsale.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.flashsale.repository.InventoryRepository;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private static final int MAX_RETRIES = 3;

    private final OrderRepository orderRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * Persist the order to MySQL and decrement both:
     * 1. flash_sale_product.sale_available (allocated stock for this sale)
     * 2. inventory.available_stock (global stock)
     * Uses optimistic locking with retry.
     * This runs AFTER the Redis Lua script has already secured the purchase.
     */
    @Transactional
    public void persistOrder(Long userId, Long flashSaleProductId, BigDecimal salePrice, String orderNo) {
        // Idempotency guard: skip if this order already exists
        if (orderRepository.existsByOrderNo(orderNo)) {
            log.warn("Order {} already persisted, skipping duplicate", orderNo);
            return;
        }

        // 1. Insert order record
        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(userId)
                .flashSaleProductId(flashSaleProductId)
                .salePrice(salePrice)
                .status(0) // CREATED
                .build();
        orderRepository.save(order);
        log.debug("Order inserted: {}", orderNo);

        // 2. Decrement flash_sale_product.sale_available
        FlashSaleProduct fsp = decrementSaleStockWithRetry(flashSaleProductId);

        // 3. Decrement inventory.available_stock (global)
        decrementInventoryWithRetry(fsp.getProductId());

        log.info("Order persisted to DB | orderNo={} user={} fspId={} productId={}",
                orderNo, userId, flashSaleProductId, fsp.getProductId());
    }

    private FlashSaleProduct decrementSaleStockWithRetry(Long flashSaleProductId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            FlashSaleProduct fsp = flashSaleProductRepository.findById(flashSaleProductId).orElseThrow(() ->
                    new IllegalStateException("FlashSaleProduct not found: " + flashSaleProductId));

            int updated = flashSaleProductRepository.decrementSaleStock(flashSaleProductId, fsp.getVersion());
            if (updated > 0) {
                log.debug("Sale stock decremented for fspId={} on attempt {}", flashSaleProductId, attempt);
                return fsp;
            }

            log.warn("Optimistic lock conflict for fspId={}, attempt {}/{}", flashSaleProductId, attempt, MAX_RETRIES);
            backoff(attempt);
        }

        log.error("CRITICAL: Failed to decrement sale stock for fspId={} after {} retries",
                flashSaleProductId, MAX_RETRIES);
        throw new IllegalStateException("Failed to decrement sale stock for fspId: " + flashSaleProductId);
    }

    private void decrementInventoryWithRetry(Long productId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow(() ->
                    new IllegalStateException("Inventory not found for productId: " + productId));

            int updated = inventoryRepository.decrementStock(productId, inventory.getVersion());
            if (updated > 0) {
                log.debug("Global inventory decremented for productId={} on attempt {}", productId, attempt);
                return;
            }

            log.warn("Optimistic lock conflict on inventory for productId={}, attempt {}/{}", productId, attempt, MAX_RETRIES);
            backoff(attempt);
        }

        log.error("CRITICAL: Failed to decrement global inventory for productId={} after {} retries",
                productId, MAX_RETRIES);
    }

    private void backoff(int attempt) {
        if (attempt < MAX_RETRIES) {
            try {
                Thread.sleep(50L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stock update interrupted", e);
            }
        }
    }
}
