package com.indie.catalog.domain.order.dto.response;

import java.time.OffsetDateTime;

/** pgOrderId — 토스 SDK requestPayment 의 orderId (orderId 는 우리 주문 번호) */
public record OrderCreated(long orderId, int amount, String pgOrderId, String status, OffsetDateTime expiresAt) {}
