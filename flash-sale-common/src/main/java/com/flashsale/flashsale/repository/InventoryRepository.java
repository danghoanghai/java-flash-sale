package com.flashsale.flashsale.repository;

import com.flashsale.flashsale.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Modifying
    @Query("UPDATE Inventory i SET i.availableStock = i.availableStock - 1 " +
           "WHERE i.productId = :productId AND i.availableStock > 0")
    int decrementStock(@Param("productId") Long productId);
}
