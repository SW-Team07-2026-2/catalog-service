package com.indie.catalog.domain.product.entity;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 재입고 판정 — 0 → N 일 때만. 5 → 3 · 3 → 5 · 0 → 0 은 재입고가 아닙니다 (08 §4) */
class StockEntityTest {

    private final OffsetDateTime now = OffsetDateTime.now();

    @Test
    void zeroToPositiveIsRestock() {
        assertThat(new StockEntity(1L, 0, now).restockTo(3, now)).isTrue();
    }

    @Test
    void positiveChangesAreNotRestock() {
        assertThat(new StockEntity(1L, 5, now).restockTo(3, now)).isFalse();
        assertThat(new StockEntity(1L, 3, now).restockTo(5, now)).isFalse();
    }

    @Test
    void zeroToZeroIsNotRestock() {
        assertThat(new StockEntity(1L, 0, now).restockTo(0, now)).isFalse();
    }

    @Test
    void sameQtyKeepsUpdatedAt() {
        StockEntity stock = new StockEntity(1L, 3, now);
        stock.restockTo(3, now.plusHours(1));
        assertThat(stock.getUpdatedAt()).isEqualTo(now);   // "n시간 전 갱신" 이 값이 같은 재수집으로 갱신되지 않게
    }
}
