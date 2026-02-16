package com.flashsale.order.listener;

import com.flashsale.flashsale.event.PurchaseEvent;
import com.flashsale.order.service.OrderPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseEventListener {

    private static final int MAX_EVENT_RETRIES = 3;

    private final OrderPersistenceService orderPersistenceService;

    @Async("orderPersistenceExecutor")
    @EventListener
    public void onPurchaseEvent(PurchaseEvent event) {
        log.info("Received PurchaseEvent | thread={} orderNo={} user={} fspId={}",
                Thread.currentThread().getName(), event.getOrderNo(), event.getUserId(), event.getFlashSaleProductId());

        for (int attempt = 1; attempt <= MAX_EVENT_RETRIES; attempt++) {
            try {
                orderPersistenceService.persistOrder(
                        event.getUserId(),
                        event.getFlashSaleProductId(),
                        event.getSalePrice(),
                        event.getOrderNo()
                );
                return;
            } catch (Exception ex) {
                log.error("Failed to persist order {} (attempt {}/{}): {}",
                        event.getOrderNo(), attempt, MAX_EVENT_RETRIES, ex.getMessage());
            }
        }

        log.error("CRITICAL: All {} retries exhausted for order {}. Requires manual reconciliation.",
                MAX_EVENT_RETRIES, event.getOrderNo());
    }
}
