package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.OrderItemEntity;

import java.util.List;

/** 주문 항목 저장소 포트 — 구현은 OrderItemJpaRepository */
public interface OrderItemRepository {

    List<OrderItemEntity> findByOrderIdOrderByOptionId(Long orderId);

    OrderItemEntity save(OrderItemEntity item);
}
