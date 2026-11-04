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
 * Payment 이벤트 — Saga ② 의 결과 반영. orderType=GOODS 만 (TICKET 은 Concert 몫).
 * <pre>
 * payment.completed  → 주문 CONFIRMED
 * payment.failed     → 주문 CANCELLED + 재고 복구 (보상)
 * payment.cancelled  → 주문 CANCELLED + 재고 복구 (사용자가 Payment 쪽에서 환불한 경우 포함)
 * </pre>
 * 멱등 — 상태 전이가 조건부 UPDATE 라 같은 이벤트가 두 번 와도 한 번만 반영됩니다 (별도 처리 테이블 불필요).
 */
@Component
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"payment.completed", "payment.failed", "payment.cancelled"})
    public void onPayment(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        if ("GOODS".equals(e.text("orderType"))) {
            long orderId = orderId(e.text("orderRef"));
            if ("payment.completed".equals(record.topic())) {
                orderService.confirm(orderId);
            } else {
                orderService.cancelLocally(orderId);
            }
        }
        ack.acknowledge();
    }

    /** 형식이 틀리면 IllegalArgumentException → 재시도 없이 DLT */
    private static long orderId(String orderRef) {
        if (orderRef == null) throw new IllegalArgumentException("payment event without orderRef");
        return Long.parseLong(orderRef);
    }
}
