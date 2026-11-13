package com.indie.catalog.domain.order.entity;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** purchase_order — 상태 전이는 OrderRepository 의 조건부 UPDATE 로만 합니다 (중복 이벤트에도 한 번만 전이) */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrderEntity {

    /** CANCELLED = 결제 전 취소 · 만료 · 결제 실패 · REFUNDED = 결제된 주문을 환불 (payment.cancelled 의 refunded) */
    public enum Status { PENDING, CONFIRMED, CANCELLED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Integer totalPrice;

    private String pgOrderId;

    @Embedded
    private ShippingSnapshot shipping;

    private OffsetDateTime createdAt;

    protected PurchaseOrderEntity() {}

    public PurchaseOrderEntity(Long userId, Integer totalPrice, ShippingSnapshot shipping, OffsetDateTime now) {
        this.userId = userId;
        this.status = Status.PENDING;
        this.totalPrice = totalPrice;
        this.shipping = shipping;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Status getStatus() { return status; }
    public Integer getTotalPrice() { return totalPrice; }
    public String getPgOrderId() { return pgOrderId; }
    public ShippingSnapshot getShipping() { return shipping; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
