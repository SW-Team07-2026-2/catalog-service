package com.indie.catalog.domain.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 주문은 항상 옵션 단위 — 상품 → 옵션을 골라 그 optionId 로 주문합니다 */
public record OrderItemRequest(@NotNull @Positive Long optionId, @NotNull @Positive @Max(1000) Integer qty) {}
