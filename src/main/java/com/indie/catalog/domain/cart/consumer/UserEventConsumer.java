package com.indie.catalog.domain.cart.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.cart.service.CartService;
import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Member -> Kafka user.withdrawn -> Catalog 장바구니 삭제와 결제 대기 주문 취소.
 * 확정 주문과 환불 주문은 보관합니다.
 */
@Component
public class UserEventConsumer {

    private final CartService cartService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final FollowerCountSync followerCountSync;

    public UserEventConsumer(CartService cartService, OrderService orderService, ObjectMapper objectMapper, FollowerCountSync followerCountSync) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.followerCountSync = followerCountSync;
    }

    /**
     * 회원 탈퇴 이벤트를 받아 장바구니와 결제 대기 주문을 정리합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = "user.withdrawn")
    public void onUserWithdrawn(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        long userId = e.requiredLong("userId");
        cartService.clear(userId);
        orderService.cancelPendingOf(userId);
        // Member에서 follow가 삭제된 뒤의 실제 수로 갱신합니다. 재전송에도 중복 차감하지 않습니다.
        followerCountSync.refreshAll();
        ack.acknowledge();
    }
}
