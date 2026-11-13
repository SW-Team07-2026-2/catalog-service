package com.indie.catalog.domain.order.dto.event;

import com.indie.catalog.domain.order.entity.OrderItemEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.event.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 이벤트 2종 — 상태 전이와 같은 트랜잭션에서 (EventPublisher = outbox). 받는 쪽은 Member (알림 · 보유 음반 자동 추가).
 * <pre>
 * order.confirmed   결제 완료로 주문 확정   userId · orderId · totalPrice · items[optionId · physicalAlbumId 또는 goodsId · name · qty]
 * order.refunded    결제된 주문을 환불      userId · orderId · itemName(대표 상품) · itemCount · refundAmount
 * </pre>
 * payment 이벤트에는 상품 이름이 없어 Member 가 알림 문장을 만들 수 없습니다 — 그래서 Catalog 가 따로 보냅니다.
 */
@Component
public class OrderEvents {

    private final EventPublisher publisher;

    public OrderEvents(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void confirmed(PurchaseOrderEntity order, List<OrderItemEntity> items, Map<Long, ProductService.Sellable> sellables) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItemEntity item : items) {
            ProductEntity product = sellables.get(item.getOptionId()).product();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("optionId", item.getOptionId());
            line.put(product instanceof PhysicalAlbumEntity ? "physicalAlbumId" : "goodsId", product.getId());
            line.put("name", product.getName());
            line.put("qty", item.getQty());
            lines.add(line);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", order.getUserId());
        p.put("orderId", order.getId());
        p.put("totalPrice", order.getTotalPrice());
        p.put("items", lines);
        publisher.publish("order.confirmed", p);
    }

    /** 환불은 전액만 있습니다 (Payment) — refundAmount 는 주문 금액 */
    public void refunded(PurchaseOrderEntity order, String itemName, int itemCount) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", order.getUserId());
        p.put("orderId", order.getId());
        p.put("itemName", itemName);
        p.put("itemCount", itemCount);
        p.put("refundAmount", order.getTotalPrice());
        publisher.publish("order.refunded", p);
    }
}
