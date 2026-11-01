package com.indie.catalog.domain.order.dto.response;

public record OrderItemView(long productId, String name, int qty, int price) {}
