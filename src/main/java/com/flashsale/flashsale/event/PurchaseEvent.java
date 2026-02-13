package com.flashsale.flashsale.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class PurchaseEvent extends ApplicationEvent {

    private final Long userId;
    private final Long flashSaleProductId;
    private final BigDecimal salePrice;
    private final String orderNo;

    public PurchaseEvent(Object source, Long userId, Long flashSaleProductId, BigDecimal salePrice, String orderNo) {
        super(source);
        this.userId = userId;
        this.flashSaleProductId = flashSaleProductId;
        this.salePrice = salePrice;
        this.orderNo = orderNo;
    }
}
