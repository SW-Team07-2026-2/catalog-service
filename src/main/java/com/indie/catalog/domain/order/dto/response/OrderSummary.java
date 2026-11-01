package com.indie.catalog.domain.order.dto.response;

import java.time.OffsetDateTime;

public record OrderSummary(long orderId, String status, int totalPrice, OffsetDateTime createdAt) {}
