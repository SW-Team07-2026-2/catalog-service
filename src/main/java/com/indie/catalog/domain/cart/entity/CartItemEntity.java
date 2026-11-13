package com.indie.catalog.domain.cart.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * cart_item — 회원이 담아 둔 옵션과 수량. 재고는 읽기만 하고 차감은 주문 생성 때 합니다 (TTL 없음).
 * 쓰기는 CartItemRepository 의 put · delete 로만 합니다.
 */
@Entity
@Table(name = "cart_item")
@IdClass(CartItemEntity.Key.class)
public class CartItemEntity {

    @Id
    private Long userId;
    @Id
    private Long optionId;
    private Integer qty;
    private OffsetDateTime createdAt;

    protected CartItemEntity() {}

    public Long getUserId() { return userId; }
    public Long getOptionId() { return optionId; }
    public Integer getQty() { return qty; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public static class Key implements Serializable {
        private Long userId;
        private Long optionId;

        public Key() {}

        public Key(Long userId, Long optionId) {
            this.userId = userId;
            this.optionId = optionId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(optionId, k.optionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, optionId);
        }
    }
}
