package com.flashsale.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class FlashSaleItemResponse {

    private Long flashSaleProductId;
    private Long productId;
    private String productName;
    private BigDecimal originalPrice;
    private BigDecimal salePrice;
    private String flashSaleName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer availableStock;
}
