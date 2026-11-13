package com.indie.catalog.domain.cart.dto.response;

import java.util.List;

/** totalPrice — 담긴 수량 그대로 계산한 합계 (품절 포함) */
public record CartView(List<CartLine> items, int totalPrice) {}
