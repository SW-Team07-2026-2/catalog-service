package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockJpaRepository extends JpaRepository<StockEntity, Long> {
}
