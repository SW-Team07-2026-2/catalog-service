package com.indie.catalog.domain.cart.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.cart.service.CartService;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * user.withdrawn (Member) — 탈퇴한 회원의 장바구니를 지우고 결제 대기(PENDING) 주문을 취소합니다.
 * 확정 · 환불된 주문과 배송 정보는 법정 보관(전자상거래법 5년) 대상이라 남깁니다.
 * 멱등 — 지울 게 없으면 0행, 취소할 주문이 없으면 아무것도 하지 않습니다.
 */
@Component
public class UserEventConsumer {

    private final CartService cartService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public UserEventConsumer(CartService cartService, OrderService orderService, ObjectMapper objectMapper) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user.withdrawn")
    public void onUserWithdrawn(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        long userId = e.requiredLong("userId");
        cartService.clear(userId);
        orderService.cancelPendingOf(userId);
        ack.acknowledge();
    }
}
