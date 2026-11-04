package com.indie.catalog.domain.order.scheduler;

import com.indie.catalog.domain.order.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제하지 않은 주문 만료 트리거 — 15분 지난 PENDING 을 1분마다 50건씩 (로직은 OrderService.expirePending).
 * 없으면 결제창에서 이탈한 주문이 재고를 영원히 잡고 있습니다.
 */
@Component
public class OrderExpiryJob {

    private static final int BATCH = 50;

    private final OrderService orderService;

    public OrderExpiryJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.expiry-interval-ms}")
    public void expire() {
        orderService.expirePending(BATCH);
    }
}
