package com.indie.catalog.domain.order.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetail(long orderId, String status, int totalPrice, String pgOrderId, OffsetDateTime createdAt,
                          ShippingView shipping, List<OrderItemView> items) {}
