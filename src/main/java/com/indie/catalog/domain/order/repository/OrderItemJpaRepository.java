package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, OrderItemEntity.Key>, OrderItemRepository {
}
