package com.indie.catalog.domain.order.client;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Payment 호출 포트 — 구현은 HttpPaymentClient (내부 토큰 · 타임아웃 3초).
 * 불통 · 5xx · 타임아웃은 전부 Unavailable — 호출자가 보상(재고 복구)합니다.
 */
public interface PaymentClient {

    /**
     * 결제 준비 — 🔴 금액은 서버가 product.price 로 계산한 값입니다. Payment 는 이 금액을 그대로 믿고,
     * 프론트가 보낸 confirm 금액을 이 값과 대조합니다. 금액 검증의 책임은 Catalog 에 있습니다 (13 §1-①).
     * albumIds 는 Member 의 컬렉션 자동 추가용 — Payment 는 내용을 모른 채 payment.completed 에 그대로 싣습니다.
     */
    Prepared prepare(long orderId, long userId, int amount, List<Long> albumIds);

    /**
     * 결제 취소 — refundApproved=false 면 승인 전(PENDING)만 무효화합니다 (만료 처리용: 방금 결제된 건을 환불하지 않게).
     * 결제 행이 없으면 NOT_FOUND (준비가 실패했던 주문). 그 외는 결제의 최종 상태 (CANCELLED · DECLINED · APPROVED).
     */
    String cancel(long orderId, String reason, boolean refundApproved);

    /** pgOrderId — 토스 결제창(SDK)의 orderId. paymentKey 는 결제창 인증 뒤 토스가 프론트에 줍니다 */
    record Prepared(String pgOrderId, String status, OffsetDateTime expiresAt) {}

    class Unavailable extends RuntimeException {
        public Unavailable(String op, Throwable cause) {
            super("payment " + op + " failed: " + cause.getMessage(), cause);
        }
    }
}
