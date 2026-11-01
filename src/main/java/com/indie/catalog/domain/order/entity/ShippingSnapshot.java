package com.indie.catalog.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 주문 당시의 받는 곳 — Member 배송지(M19)의 복사본이라 원본을 고치거나 지워도 바뀌지 않습니다 */
@Embeddable
public record ShippingSnapshot(String recipientName,
                               @Column(name = "recipient_phone") String phone,
                               String zonecode,
                               String address,
                               String addressDetail) {}
