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
 * Catalog 주문 확정과 환불 -> Outbox -> Kafka -> Member 알림과 보유 음반 반영.
 * 주문 상태 변경과 같은 트랜잭션에서 상품 정보를 포함해 저장합니다.
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

    /** 환불은 전액만 있습니다 (Payment) - refundAmount 는 주문 금액 */
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
