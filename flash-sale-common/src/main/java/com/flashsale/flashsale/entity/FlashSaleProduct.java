package com.flashsale.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "flash_sale_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flash_sale_id", nullable = false)
    private Long flashSaleId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sale_price", nullable = false)
    private BigDecimal salePrice;

    @Column(name = "sale_stock", nullable = false)
    private Integer saleStock;

    @Column(name = "sale_available", nullable = false)
    private Integer saleAvailable;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean enabled;
}
