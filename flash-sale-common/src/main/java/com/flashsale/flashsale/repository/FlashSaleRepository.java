package com.flashsale.flashsale.repository;

import com.flashsale.flashsale.entity.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    @Query("SELECT fs FROM FlashSale fs WHERE fs.status = 1 AND fs.startTime <= :now AND fs.endTime >= :now")
    List<FlashSale> findActiveAt(@Param("now") LocalDateTime now);

    List<FlashSale> findByStatus(Integer status);
}
