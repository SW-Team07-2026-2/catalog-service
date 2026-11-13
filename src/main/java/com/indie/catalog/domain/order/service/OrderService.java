package com.indie.catalog.domain.order.service;

import com.indie.catalog.domain.cart.repository.CartItemRepository;
import com.indie.catalog.domain.order.client.PaymentClient;
import com.indie.catalog.domain.order.dto.event.OrderEvents;
import com.indie.catalog.domain.order.dto.response.OrderCreated;
import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderItemView;
import com.indie.catalog.domain.order.dto.response.OrderSummary;
import com.indie.catalog.domain.order.dto.response.ShippingView;
import com.indie.catalog.domain.order.entity.OrderItemEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity.Status;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.exception.OrderErrorCode;
import com.indie.catalog.domain.order.repository.OrderItemRepository;
import com.indie.catalog.domain.order.repository.OrderRepository;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.domain.product.service.ProductService.Sellable;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.CursorPage;
import com.indie.catalog.global.common.util.Cursor;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 주문 — Saga ② (11 §5 · 15 §5.1). Catalog 가 흐름을 지휘하고 보상(재고 복구)까지 책임집니다.
 * 주문은 항상 옵션 단위입니다 (상품 → 옵션 → 주문 항목). 가격 · 재고 모두 옵션 것을 씁니다.
 * <pre>
 * ① 재고 차감 + order PENDING        (로컬 트랜잭션)
 * ② Payment prepare → pgOrderId      (동기 · 실패하면 즉시 ① 을 되돌림)
 * ③ 프론트가 토스 결제창 → Payment 에 confirm (금액 대조는 Payment)
 * ④ payment.completed → CONFIRMED + order.confirmed + 장바구니에서 빼기
 *    payment.failed · payment.cancelled(refunded=false) → CANCELLED + 재고 복구
 *    payment.cancelled(refunded=true) → REFUNDED + 재고 복구 + order.refunded
 * ⑤ 15분 안에 결제하지 않으면 만료     (OrderExpiryJob)
 * </pre>
 * 상태 전이는 전부 조건부 UPDATE — 중복 이벤트 · 동시 취소에도 재고는 한 번만 복구됩니다.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final StockRepository stockRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    private final PaymentClient paymentClient;
    private final OrderEvents events;
    private final TransactionTemplate tx;
    private final CatalogProperties.Order props;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, OrderItemRepository itemRepository, StockRepository stockRepository,
                        CartItemRepository cartItemRepository, ProductService productService, PaymentClient paymentClient,
                        OrderEvents events, TransactionTemplate tx, CatalogProperties props, Clock clock) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.stockRepository = stockRepository;
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
        this.paymentClient = paymentClient;
        this.events = events;
        this.tx = tx;
        this.props = props.order();
        this.clock = clock;
    }

    /** C9 — 🔴 요청에 금액이 없습니다. 금액은 옵션 가격으로만 계산합니다 (13 §1-①) */
    public OrderCreated create(long userId, List<Line> lines, ShippingSnapshot shipping) {
        validate(lines);
        Reserved reserved = tx.execute(s -> reserve(userId, lines, shipping));
        PaymentClient.Prepared prepared;
        try {
            prepared = paymentClient.prepare(reserved.orderId(), userId, reserved.amount());
        } catch (PaymentClient.Unavailable e) {
            // 결제 준비 전이라 사용자는 아직 돈을 낼 수 없습니다 — 바로 되돌려도 안전합니다
            log.warn("payment_prepare_failed orderId={} error={}", reserved.orderId(), e.getMessage());
            cancelLocally(reserved.orderId());
            throw new GeneralException(OrderErrorCode.PAYMENT_UNAVAILABLE);
        }
        tx.executeWithoutResult(s -> orderRepository.attachPgOrderId(reserved.orderId(), prepared.pgOrderId()));
        return new OrderCreated(reserved.orderId(), reserved.amount(), prepared.pgOrderId(), "PENDING", prepared.expiresAt());
    }

    /** C10 — 내 주문 목록 (커서 · 최신순) */
    @Transactional(readOnly = true)
    public CursorPage<OrderSummary> list(long userId, Cursor cursor, int size) {
        List<OrderSummary> items = orderRepository.findPage(userId, cursor, size + 1).stream()
                .map(o -> new OrderSummary(o.getId(), o.getStatus().name(), o.getTotalPrice(), o.getCreatedAt())).toList();
        return CursorPage.of(items, size, o -> new Cursor(null, o.orderId()));
    }

    /** C11 — 남의 주문은 404 (403 이 아님 — 존재 여부도 알려주지 않습니다) */
    @Transactional(readOnly = true)
    public OrderDetail get(long userId, long orderId) {
        PurchaseOrderEntity order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        List<OrderItemEntity> items = itemRepository.findByOrderIdOrderByOptionId(orderId);
        Map<Long, Sellable> sellables = productService.sellables(items.stream().map(OrderItemEntity::getOptionId).toList());
        return new OrderDetail(order.getId(), order.getStatus().name(), order.getTotalPrice(), order.getPgOrderId(),
                order.getCreatedAt(), ShippingView.of(order.getShipping()), items.stream().map(i -> {
                    Sellable s = sellables.get(i.getOptionId());   // order_item.option_id 는 FK — 항상 있습니다
                    return new OrderItemView(i.getOptionId(), s.product().getId(), s.product().getProductType(),
                            s.product().getName(), s.option().getName(), i.getQty(), i.getPrice());
                }).toList());
    }

    /**
     * C12 — 결제 전이면 무효화, 결제 후면 환불까지. Payment 를 먼저 취소하고(결제 여부의 최종 판정자) 그 다음 재고를 복구합니다.
     * 순서가 반대면 "재고는 돌려놨는데 결제는 승인됨"이 생깁니다.
     * 여기서는 주문 상태로 환불 여부를 짐작합니다 (CONFIRMED = 결제됨 → REFUNDED). 결제는 됐는데 payment.completed 를 아직
     * 못 받은 경우엔 CANCELLED 로 남지만, 뒤따라오는 payment.cancelled(refunded=true) 가 REFUNDED 로 바로잡습니다.
     */
    public OrderDetail cancel(long userId, long orderId) {
        PurchaseOrderEntity order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        if (order.getStatus() == Status.PENDING || order.getStatus() == Status.CONFIRMED) {
            try {
                paymentClient.cancel(orderId, "USER_CANCEL", true);
            } catch (PaymentClient.Unavailable e) {
                log.warn("payment_cancel_failed orderId={} error={}", orderId, e.getMessage());
                throw new GeneralException(OrderErrorCode.PAYMENT_UNAVAILABLE, "주문을 취소하지 못했습니다. 잠시 후 다시 시도해주세요");
            }
            if (order.getStatus() == Status.CONFIRMED) refundLocally(orderId);
            else cancelLocally(orderId);
        }
        return get(userId, orderId);
    }

    /** payment.completed — 주문 확정 + order.confirmed + 산 옵션을 장바구니에서 뺍니다. 중복 이벤트면 0행이라 아무것도 하지 않습니다 */
    @Transactional
    public void confirm(long orderId) {
        if (orderRepository.confirm(orderId) == 1) {
            PurchaseOrderEntity order = orderRepository.findById(orderId).orElseThrow();
            List<OrderItemEntity> items = itemRepository.findByOrderIdOrderByOptionId(orderId);
            List<Long> optionIds = items.stream().map(OrderItemEntity::getOptionId).toList();
            cartItemRepository.deleteOptions(order.getUserId(), optionIds);
            events.confirmed(order, items, productService.sellables(optionIds));
            return;
        }
        orderRepository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() == Status.CANCELLED) {
                // 취소 · 만료는 Payment 를 먼저 취소하므로 곧 payment.cancelled(refunded=true) 가 와서 REFUNDED 가 됩니다.
                // 오지 않으면 수동 환불 대상
                log.error("payment_completed_for_cancelled_order orderId={}", orderId);
            }
        });
    }

    /**
     * 주문 취소 + 재고 복구 — 돈이 오가지 않은 취소 (결제 전 취소 · 만료 · 결제 실패).
     * 이미 취소 · 환불된 주문이면 아무것도 하지 않습니다 (재고 이중 복구 방지).
     * create · cancel 안에서도 부르므로 @Transactional(자기 호출이면 무시됨) 대신 TransactionTemplate 을 씁니다.
     */
    public boolean cancelLocally(long orderId) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            if (orderRepository.cancel(orderId) == 0) return false;
            restoreStock(itemRepository.findByOrderIdOrderByOptionId(orderId));
            log.info("order_cancelled orderId={}", orderId);
            return true;
        }));
    }

    /**
     * 환불 — 결제된 주문을 Payment 가 환불했을 때 (payment.cancelled 의 refunded=true · 확정 주문의 사용자 취소).
     * REFUNDED 로 바꾸고 order.refunded 를 냅니다. 재고는 아직 복구하지 않은 경우(PENDING · CONFIRMED 였던 주문)에만 복구합니다 —
     * 먼저 CANCELLED 로 기록됐던 주문은 그때 이미 복구했습니다. 이미 REFUNDED 면 아무것도 하지 않습니다 (중복 이벤트).
     */
    public void refundLocally(long orderId) {
        tx.executeWithoutResult(s -> {
            boolean restore = orderRepository.refund(orderId) == 1;
            if (!restore && orderRepository.refundCancelled(orderId) == 0) return;
            List<OrderItemEntity> items = itemRepository.findByOrderIdOrderByOptionId(orderId);
            if (restore) restoreStock(items);
            PurchaseOrderEntity order = orderRepository.findById(orderId).orElseThrow();
            String itemName = productService.sellables(List.of(items.get(0).getOptionId())).get(items.get(0).getOptionId())
                    .product().getName();
            events.refunded(order, itemName, items.size());
            log.info("order_refunded orderId={}", orderId);
        });
    }

    /**
     * 결제창에서 이탈한 주문 정리 — PENDING 이 ttl 을 넘기면 결제 무효화 → 재고 복구.
     * replica 2 가 같은 주문을 동시에 집어도 안전합니다 — Payment 취소는 멱등, 재고 복구는 조건부 UPDATE 로 1회.
     */
    public void expirePending(int batch) {
        OffsetDateTime before = OffsetDateTime.now(clock).minusMinutes(props.pendingTtlMinutes());
        for (Long orderId : orderRepository.findExpiredPending(before, batch)) {
            if (!voidUnpaid(orderId, "EXPIRED")) return;   // Payment 가 살아나면 다음 주기에 이어서
        }
    }

    /** 회원 탈퇴 — 결제 대기 주문을 취소합니다. Payment 가 불통이면 남은 주문은 만료 처리(expirePending)가 정리합니다 */
    public void cancelPendingOf(long userId) {
        for (Long orderId : orderRepository.findPendingIdsByUserId(userId)) {
            if (!voidUnpaid(orderId, "USER_WITHDRAWN")) return;
        }
    }

    /**
     * 결제하지 않은 주문 무효화. Payment 를 먼저 취소합니다 (refundApproved=false). 그 사이 결제가 승인됐다면 APPROVED 가 돌아오고
     * 주문은 건드리지 않습니다 — payment.completed 가 곧 CONFIRMED 로 만듭니다. Payment 불통이면 false.
     */
    private boolean voidUnpaid(long orderId, String reason) {
        String status;
        try {
            status = paymentClient.cancel(orderId, reason, false);
        } catch (PaymentClient.Unavailable e) {
            log.warn("order_void_paused orderId={} reason={} error={}", orderId, reason, e.getMessage());
            return false;
        }
        if ("APPROVED".equals(status)) {
            log.info("order_void_skipped_paid orderId={} reason={}", orderId, reason);
        } else {
            cancelLocally(orderId);
        }
        return true;
    }

    private Reserved reserve(long userId, List<Line> lines, ShippingSnapshot shipping) {
        List<Line> sorted = lines.stream().sorted(Comparator.comparingLong(Line::optionId)).toList();   // 잠금 순서 고정 → 교착 방지
        Map<Long, Sellable> sellables = productService.sellables(sorted.stream().map(Line::optionId).toList());
        int amount = 0;
        for (Line line : sorted) {
            Sellable s = sellables.get(line.optionId());
            if (s == null) throw new GeneralException(ProductErrorCode.OPTION_NOT_FOUND);
            if (stockRepository.decrement(line.optionId(), line.qty()) == 0) {
                throw new GeneralException(ProductErrorCode.OUT_OF_STOCK,
                        s.product().getName() + " (" + s.option().getName() + ") 의 재고가 부족합니다");   // 롤백 → 앞서 차감한 것도 복구
            }
            amount = Math.addExact(amount, Math.multiplyExact(s.option().getPrice(), line.qty()));
        }
        PurchaseOrderEntity order = orderRepository.save(new PurchaseOrderEntity(userId, amount, shipping, OffsetDateTime.now(clock)));
        for (Line line : sorted) {
            itemRepository.save(new OrderItemEntity(order.getId(), line.optionId(), line.qty(),
                    sellables.get(line.optionId()).option().getPrice()));
        }
        return new Reserved(order.getId(), amount);
    }

    /** 🔴 재입고 알림 같은 이벤트를 내지 않습니다 — 보상 복구는 입고가 아닙니다 */
    private void restoreStock(List<OrderItemEntity> items) {
        for (OrderItemEntity item : items) stockRepository.restore(item.getOptionId(), item.getQty());
    }

    private void validate(List<Line> lines) {
        if (lines.size() > props.maxItems()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "한 번에 최대 " + props.maxItems() + "개 상품까지 주문할 수 있습니다");
        }
        if (lines.stream().map(Line::optionId).distinct().count() != lines.size()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "같은 상품이 두 번 들어 있습니다");
        }
        if (lines.stream().anyMatch(l -> l.qty() > props.maxQty())) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "상품당 최대 " + props.maxQty() + "개까지 주문할 수 있습니다");
        }
    }

    public record Line(long optionId, int qty) {}

    private record Reserved(long orderId, int amount) {}
}
