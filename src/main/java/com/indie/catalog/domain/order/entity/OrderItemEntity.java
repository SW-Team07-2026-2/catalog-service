package com.indie.catalog.domain.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** order_item — price 는 주문 시점 가격 고정 (상품 가격이 바뀌어도 주문 금액은 그대로) */
@Entity
@Table(name = "order_item")
@IdClass(OrderItemEntity.Key.class)
public class OrderItemEntity {

    @Id
    private Long orderId;

    @Id
    private Long productId;

    private Integer qty;

    private Integer price;

    protected OrderItemEntity() {}

    public OrderItemEntity(Long orderId, Long productId, Integer qty, Integer price) {
        this.orderId = orderId;
        this.productId = productId;
        this.qty = qty;
        this.price = price;
    }

    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public Integer getQty() { return qty; }
    public Integer getPrice() { return price; }

    public static class Key implements Serializable {
        private Long orderId;
        private Long productId;

        public Key() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(orderId, k.orderId) && Objects.equals(productId, k.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, productId);
        }
    }
}
