package com.flashsale.wallet.repository;

import com.flashsale.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    boolean existsByReferenceId(String referenceId);
}
