package com.flashsale.order.service;

import com.flashsale.flashsale.entity.FlashSaleProduct;
import com.flashsale.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.flashsale.repository.InventoryRepository;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.wallet.entity.TransactionType;
import com.flashsale.wallet.entity.WalletTransaction;
import com.flashsale.wallet.repository.WalletRepository;
import com.flashsale.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final InventoryRepository inventoryRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Persist the order to MySQL and:
     * 1. Insert order record
     * 2. Deduct wallet balance (DB) and write ledger
     * 3. Decrement flash_sale_product.sale_available 
     * 4. Decrement inventory.available_stock 
     * Uses Native DB Atomic Update (Row-level lock). NO retries, NO Thread.sleep().
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

        // 2. Deduct wallet in DB and write ledger
        deductWalletAtomically(userId, salePrice);
        ensureLedgerRecord(userId, salePrice, orderNo);

        // Lấy thông tin fsp để biết productId (chỉ SELECT, không lock)
        FlashSaleProduct fsp = flashSaleProductRepository.findById(flashSaleProductId).orElseThrow(() ->
                new IllegalStateException("FlashSaleProduct not found: " + flashSaleProductId));

        // 3. Decrement flash_sale_product.sale_available (Atomic)
        decrementSaleStockAtomically(flashSaleProductId);

        // 4. Decrement inventory.available_stock (Atomic)
        decrementInventoryAtomically(fsp.getProductId());

        log.info("Order persisted to DB | orderNo={} user={} fspId={} productId={}",
                orderNo, userId, flashSaleProductId, fsp.getProductId());
    }

    private void deductWalletAtomically(Long userId, BigDecimal amount) {
        // MySQL sẽ tự động lock dòng user này và trừ tiền an toàn tuyệt đối
        int updated = walletRepository.deductBalance(userId, amount);
        if (updated == 0) {
            log.error("CRITICAL: Failed to deduct DB wallet for userId={}. Insufficient balance.", userId);
            throw new IllegalStateException("Failed to deduct wallet for userId: " + userId);
        }
        log.debug("Wallet deducted atomically for userId={} amount={}", userId, amount);
    }

    private void ensureLedgerRecord(Long userId, BigDecimal salePrice, String orderNo) {
        if (walletTransactionRepository.existsByReferenceId(orderNo)) {
            return;
        }
        WalletTransaction tx = WalletTransaction.builder()
                .userId(userId)
                .amount(salePrice.negate())
                .referenceId(orderNo)
                .type(TransactionType.FLASH_SALE_DEDUCT)
                .build();
        walletTransactionRepository.save(tx);
    }

    private void decrementSaleStockAtomically(Long flashSaleProductId) {
        int updated = flashSaleProductRepository.decrementSaleStock(flashSaleProductId);
        if (updated == 0) {
            log.error("CRITICAL: DB Sale stock is 0 for fspId={}", flashSaleProductId);
            throw new IllegalStateException("Sale stock empty for fspId: " + flashSaleProductId);
        }
    }

    private void decrementInventoryAtomically(Long productId) {
        int updated = inventoryRepository.decrementStock(productId);
        if (updated == 0) {
            log.error("CRITICAL: Global inventory is 0 for productId={}", productId);
            throw new IllegalStateException("Global inventory empty for productId: " + productId);
        }
    }
}