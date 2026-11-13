package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<PurchaseOrderEntity, Long> {

    Optional<PurchaseOrderEntity> findByIdAndUserId(Long id, Long userId);
}
