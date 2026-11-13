package com.indie.catalog.domain.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** order_item — 주문한 옵션과 수량. price 는 주문 시점 가격 고정 (옵션 가격이 바뀌어도 주문 금액은 그대로) */
@Entity
@Table(name = "order_item")
@IdClass(OrderItemEntity.Key.class)
public class OrderItemEntity {

    @Id
    private Long orderId;

    @Id
    private Long optionId;

    private Integer qty;

    private Integer price;

    protected OrderItemEntity() {}

    public OrderItemEntity(Long orderId, Long optionId, Integer qty, Integer price) {
        this.orderId = orderId;
        this.optionId = optionId;
        this.qty = qty;
        this.price = price;
    }

    public Long getOrderId() { return orderId; }
    public Long getOptionId() { return optionId; }
    public Integer getQty() { return qty; }
    public Integer getPrice() { return price; }

    public static class Key implements Serializable {
        private Long orderId;
        private Long optionId;

        public Key() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(orderId, k.orderId) && Objects.equals(optionId, k.optionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, optionId);
        }
    }
}
