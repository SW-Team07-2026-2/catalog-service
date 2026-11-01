package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 판매 정보 — 우리가 주 판매처. purchase_url 은 실제 판매처(크롤링) 링크 1개.
 * qty 는 입고 수량(절대값) · null 이면 수량은 그대로. 기존 재고 0 → N 이면 stock.restocked.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SaleUpsert(@NotNull @PositiveOrZero Integer price,
                         @PositiveOrZero Integer qty,
                         @Size(max = 1024) String purchaseUrl) {}
