package com.indie.catalog.domain.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 담을 수량 (더하는 값이 아니라 그 옵션의 최종 수량) — 상한은 주문과 같습니다 (app.order.max-qty) */
public record CartPutRequest(@NotNull @Positive Integer qty) {}
