package com.flashsale.flashsale.repository;

import com.flashsale.flashsale.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
