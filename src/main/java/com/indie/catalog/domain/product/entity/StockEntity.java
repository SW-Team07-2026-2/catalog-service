package com.indie.catalog.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** stock — 상품별 재고 1행. 주문 차감 · 보상 복구는 StockRepository 의 조건부 UPDATE 로만 합니다 */
@Entity
@Table(name = "stock")
public class StockEntity {

    @Id
    private Long productId;

    private Integer qty;

    private OffsetDateTime updatedAt;

    protected StockEntity() {}

    public StockEntity(Long productId, Integer qty, OffsetDateTime now) {
        this.productId = productId;
        this.qty = qty;
        this.updatedAt = now;
    }

    /** 입고 — 수량을 절대값으로 맞춥니다. 0 → N 이면 true (재입고) */
    public boolean restockTo(int qty, OffsetDateTime now) {
        boolean restocked = this.qty == 0 && qty > 0;
        if (this.qty != qty) {
            this.qty = qty;
            this.updatedAt = now;
        }
        return restocked;
    }

    public Long getProductId() { return productId; }
    public Integer getQty() { return qty; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
