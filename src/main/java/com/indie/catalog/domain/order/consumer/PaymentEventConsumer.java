package com.indie.catalog.domain.order.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.global.event.EventEnvelope;
import com.indie.catalog.domain.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Payment -> Kafka payment.completed, payment.failed, payment.cancelled -> Catalog 주문 반영.
 * GOODS 주문만 처리하며 완료는 확정, 실패와 취소는 재고 복구, refunded=true는 환불로 반영합니다.
 * 조건부 상태 변경으로 중복 이벤트에 따른 재고 이중 복구를 막습니다.
 */
@Component
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * 굿즈 결제 이벤트를 받아 주문 확정, 취소 또는 환불을 반영합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = {"payment.completed", "payment.failed", "payment.cancelled"})
    public void onPayment(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        if ("GOODS".equals(e.text("orderType"))) {
            long orderId = orderId(e.text("orderRef"));
            if ("payment.completed".equals(record.topic())) {
                orderService.confirm(orderId);
            } else if (e.payload().path("refunded").asBoolean(false)) {
                orderService.refundLocally(orderId);
            } else {
                orderService.cancelLocally(orderId);
            }
        }
        ack.acknowledge();
    }

    /** 형식이 틀리면 IllegalArgumentException -> 재시도 없이 DLT */
    private static long orderId(String orderRef) {
        if (orderRef == null) throw new IllegalArgumentException("payment event without orderRef");
        return Long.parseLong(orderRef);
    }
}
