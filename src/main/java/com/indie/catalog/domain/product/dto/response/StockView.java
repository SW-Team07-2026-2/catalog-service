package com.indie.catalog.domain.product.dto.response;

import java.time.OffsetDateTime;

/** 재고 조정 결과 — 조정 뒤 수량 */
public record StockView(Long optionId, int qty, OffsetDateTime updatedAt) {}
