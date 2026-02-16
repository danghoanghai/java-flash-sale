package com.flashsale.wallet.repository;

import com.flashsale.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Modifying
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount " +
       "WHERE w.userId = :userId AND w.balance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
