package com.indie.catalog.domain.order.client;

import java.time.OffsetDateTime;

/**
 * Catalog -> Payment 결제 준비와 취소 포트. 구현은 HttpPaymentClient입니다.
 * 연결 실패, 5xx, 타임아웃은 Unavailable로 전달하고 호출자가 보상을 처리합니다.
 */
public interface PaymentClient {

    /**
     * 서버에서 계산한 주문 금액으로 Payment에 결제 준비를 요청합니다.
     *
     * @param orderId Catalog 주문 ID
     * @param userId 조회하거나 처리할 회원 ID
     * @param amount 결제 금액(원)
     */
    Prepared prepare(long orderId, long userId, int amount);

    /**
     * Payment에 취소를 요청합니다. 환불 허용 여부에 따라 승인된 결제도 취소합니다.
     * 결제가 없으면 NOT_FOUND, 그 외에는 최종 결제 상태를 반환합니다.
     *
     * @param orderId Catalog 주문 ID
     * @param reason 취소 또는 재고 변경 사유
     * @param refundApproved 이미 승인된 결제의 환불 허용 여부
     */
    String cancel(long orderId, String reason, boolean refundApproved);

    /** pgOrderId - 토스 결제창(SDK)의 orderId. paymentKey 는 결제창 인증 뒤 토스가 프론트에 줍니다 */
    record Prepared(String pgOrderId, String status, OffsetDateTime expiresAt) {}

    class Unavailable extends RuntimeException {
        public Unavailable(String op, Throwable cause) {
            super("payment " + op + " failed: " + cause.getMessage(), cause);
        }
    }
}
