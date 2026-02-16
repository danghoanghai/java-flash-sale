package com.flashsale.flashsale.repository;

import com.flashsale.flashsale.entity.FlashSaleProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlashSaleProductRepository extends JpaRepository<FlashSaleProduct, Long> {

    List<FlashSaleProduct> findByFlashSaleIdAndEnabledTrue(Long flashSaleId);

    @Modifying
    @Query("UPDATE FlashSaleProduct fsp SET fsp.saleAvailable = fsp.saleAvailable - 1 " +
        "WHERE fsp.id = :flashSaleProductId AND fsp.saleAvailable > 0")
    int decrementSaleStock(@Param("flashSaleProductId") Long flashSaleProductId);
}
