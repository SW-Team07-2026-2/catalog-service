package com.indie.catalog.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * stock — 옵션별 재고 1행 (옵션과 1:1). 주문 경합의 행 락 대상이라 옵션과 떼어 둡니다.
 * 주문 차감 · 보상 복구는 StockRepository 의 조건부 UPDATE 로만 합니다.
 */
@Entity
@Table(name = "stock")
public class StockEntity {

    @Id
    private Long optionId;
    private Integer qty;
    private OffsetDateTime updatedAt;

    protected StockEntity() {}

    public StockEntity(Long optionId, Integer qty, OffsetDateTime now) {
        this.optionId = optionId;
        this.qty = qty;
        this.updatedAt = now;
    }

    /** 입고 — 수량을 절대값으로 맞춥니다. 값이 같으면 updated_at 도 그대로 ("n시간 전 갱신"이 재수집으로 바뀌지 않게) */
    public void restockTo(int qty, OffsetDateTime now) {
        if (this.qty != qty) {
            this.qty = qty;
            this.updatedAt = now;
        }
    }

    public Long getOptionId() { return optionId; }
    public Integer getQty() { return qty; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
