package com.indie.catalog.domain.order.service;

import com.indie.catalog.domain.album.entity.LpEntity;
import com.indie.catalog.domain.album.repository.LpRepository;
import com.indie.catalog.domain.order.client.PaymentClient;
import com.indie.catalog.domain.order.dto.response.OrderCreated;
import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderItemView;
import com.indie.catalog.domain.order.dto.response.OrderSummary;
import com.indie.catalog.domain.order.dto.response.ShippingView;
import com.indie.catalog.domain.order.entity.OrderItemEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import com.indie.catalog.domain.order.exception.OrderErrorCode;
import com.indie.catalog.domain.order.repository.OrderItemRepository;
import com.indie.catalog.domain.order.repository.OrderRepository;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.repository.ProductRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.CursorPage;
import com.indie.catalog.global.common.util.Cursor;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 주문 — Saga ② (11 §5 · 15 §5.1). Catalog 가 흐름을 지휘하고 보상(재고 복구)까지 책임집니다.
 * <pre>
 * ① 재고 차감 + order PENDING        (로컬 트랜잭션)
 * ② Payment prepare → pgOrderId      (동기 · 실패하면 즉시 ① 을 되돌림)
 * ③ 프론트가 토스 결제창 → Payment 에 confirm (금액 대조는 Payment)
 * ④ payment.completed → CONFIRMED    payment.failed · payment.cancelled → CANCELLED + 재고 복구
 * ⑤ 15분 안에 결제하지 않으면 만료     (OrderExpiryJob)
 * </pre>
 * 상태 전이는 전부 조건부 UPDATE — 중복 이벤트 · 동시 취소에도 재고는 한 번만 복구됩니다.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final LpRepository lpRepository;
    private final PaymentClient paymentClient;
    private final TransactionTemplate tx;
    private final CatalogProperties.Order props;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, OrderItemRepository itemRepository,
                        ProductRepository productRepository, StockRepository stockRepository, LpRepository lpRepository,
                        PaymentClient paymentClient, TransactionTemplate tx, CatalogProperties props, Clock clock) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.lpRepository = lpRepository;
        this.paymentClient = paymentClient;
        this.tx = tx;
        this.props = props.order();
        this.clock = clock;
    }

    /** C9 — 🔴 요청에 금액이 없습니다. 금액은 product.price 로만 계산합니다 (13 §1-①) */
    public OrderCreated create(long userId, List<Line> lines, ShippingSnapshot shipping) {
        validate(lines);
        Reserved reserved = tx.execute(s -> reserve(userId, lines, shipping));
        PaymentClient.Prepared prepared;
        try {
            prepared = paymentClient.prepare(reserved.orderId(), userId, reserved.amount(), reserved.albumIds());
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
        Limit limit = Limit.of(size + 1);
        List<PurchaseOrderEntity> rows = cursor == null
                ? orderRepository.findByUserIdOrderByIdDesc(userId, limit)
                : orderRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor.id(), limit);
        List<OrderSummary> items = rows.stream()
                .map(o -> new OrderSummary(o.getId(), o.getStatus().name(), o.getTotalPrice(), o.getCreatedAt())).toList();
        return CursorPage.of(items, size, o -> new Cursor(null, o.orderId()));
    }

    /** C11 — 남의 주문은 404 (403 이 아님 — 존재 여부도 알려주지 않습니다) */
    @Transactional(readOnly = true)
    public OrderDetail get(long userId, long orderId) {
        PurchaseOrderEntity order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        List<OrderItemEntity> items = itemRepository.findByOrderIdOrderByProductId(orderId);
        Map<Long, String> names = productRepository.findAllById(items.stream().map(OrderItemEntity::getProductId).toList())
                .stream().collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
        return new OrderDetail(order.getId(), order.getStatus().name(), order.getTotalPrice(), order.getPgOrderId(),
                order.getCreatedAt(), ShippingView.of(order.getShipping()), items.stream()
                .map(i -> new OrderItemView(i.getProductId(), names.get(i.getProductId()), i.getQty(), i.getPrice())).toList());
    }

    /**
     * C12 — 결제 전이면 무효화, 결제 후면 환불까지. Payment 를 먼저 취소하고(결제 여부의 최종 판정자) 그 다음 재고를 복구합니다.
     * 순서가 반대면 "재고는 돌려놨는데 결제는 승인됨"이 생깁니다.
     */
    public OrderDetail cancel(long userId, long orderId) {
        PurchaseOrderEntity order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        if (order.getStatus() != PurchaseOrderEntity.Status.CANCELLED) {
            try {
                paymentClient.cancel(orderId, "USER_CANCEL", true);
            } catch (PaymentClient.Unavailable e) {
                log.warn("payment_cancel_failed orderId={} error={}", orderId, e.getMessage());
                throw new GeneralException(OrderErrorCode.PAYMENT_UNAVAILABLE, "주문을 취소하지 못했습니다. 잠시 후 다시 시도해주세요");
            }
            cancelLocally(orderId);
        }
        return get(userId, orderId);
    }

    /** payment.completed — 중복 이벤트면 0행 */
    @Transactional
    public void confirm(long orderId) {
        if (orderRepository.confirm(orderId) == 1) return;
        orderRepository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() == PurchaseOrderEntity.Status.CANCELLED) {
                // 취소 · 만료는 Payment 를 먼저 취소하므로 정상 흐름에서는 생기지 않습니다 — 생기면 수동 환불 대상
                log.error("payment_completed_for_cancelled_order orderId={}", orderId);
            }
        });
    }

    /**
     * 주문 취소 + 재고 복구. 🔴 재입고 이벤트를 내지 않습니다 — 보상 복구로 0 → 1 이 되는 건 재입고가 아닙니다.
     * 이미 취소된 주문이면 아무것도 하지 않습니다 (재고 이중 복구 방지).
     * create · cancel 안에서도 부르므로 @Transactional(자기 호출이면 무시됨) 대신 TransactionTemplate 을 씁니다.
     */
    public boolean cancelLocally(long orderId) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            if (orderRepository.cancel(orderId) == 0) return false;
            for (OrderItemEntity item : itemRepository.findByOrderIdOrderByProductId(orderId)) {
                stockRepository.restore(item.getProductId(), item.getQty());
            }
            log.info("order_cancelled orderId={}", orderId);
            return true;
        }));
    }

    /**
     * 결제창에서 이탈한 주문 정리 — PENDING 이 ttl 을 넘기면 결제 무효화 → 재고 복구.
     * <ul>
     *   <li>Payment 를 먼저 취소합니다 (refundApproved=false). 그 사이 결제가 승인됐다면 APPROVED 가 돌아오고,
     *       주문은 건드리지 않습니다 — payment.completed 가 곧 CONFIRMED 로 만듭니다</li>
     *   <li>replica 2 가 같은 주문을 동시에 집어도 안전합니다 — Payment 취소는 멱등, 재고 복구는 조건부 UPDATE 로 1회</li>
     * </ul>
     */
    public void expirePending(int batch) {
        OffsetDateTime before = OffsetDateTime.now(clock).minusMinutes(props.pendingTtlMinutes());
        for (Long orderId : orderRepository.findExpiredPending(before, batch)) {
            String status;
            try {
                status = paymentClient.cancel(orderId, "EXPIRED", false);
            } catch (PaymentClient.Unavailable e) {
                log.warn("order_expiry_paused orderId={} error={}", orderId, e.getMessage());
                return;   // Payment 가 살아나면 다음 주기에 이어서
            }
            if ("APPROVED".equals(status)) {
                log.info("order_expiry_skipped_paid orderId={}", orderId);
                continue;
            }
            cancelLocally(orderId);
        }
    }

    private Reserved reserve(long userId, List<Line> lines, ShippingSnapshot shipping) {
        List<Line> sorted = lines.stream().sorted(Comparator.comparingLong(Line::productId)).toList();   // 잠금 순서 고정 → 교착 방지
        Map<Long, ProductEntity> products = productRepository.findAllById(sorted.stream().map(Line::productId).toList())
                .stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        int amount = 0;
        for (Line line : sorted) {
            ProductEntity product = products.get(line.productId());
            if (product == null) throw new GeneralException(ProductErrorCode.PRODUCT_NOT_FOUND);
            if (stockRepository.decrement(line.productId(), line.qty()) == 0) {
                throw new GeneralException(ProductErrorCode.OUT_OF_STOCK, product.getName() + " 의 재고가 부족합니다");   // 롤백 → 앞서 차감한 것도 복구
            }
            amount = Math.addExact(amount, Math.multiplyExact(product.getPrice(), line.qty()));
        }
        PurchaseOrderEntity order = orderRepository.save(new PurchaseOrderEntity(userId, amount, shipping, OffsetDateTime.now(clock)));
        for (Line line : sorted) {
            itemRepository.save(new OrderItemEntity(order.getId(), line.productId(), line.qty(),
                    products.get(line.productId()).getPrice()));
        }
        return new Reserved(order.getId(), amount, albumIds(products.values()));
    }

    /** 컬렉션 자동 추가용 — CD 는 album.id, LP 는 lp.album_id, 굿즈는 없음 */
    private List<Long> albumIds(Iterable<ProductEntity> products) {
        List<Long> cds = new ArrayList<>();
        List<Long> lpIds = new ArrayList<>();
        for (ProductEntity p : products) {
            if (p.getProductType() == ProductEntity.Type.CD) cds.add(p.getRefId());
            if (p.getProductType() == ProductEntity.Type.LP) lpIds.add(p.getRefId());
        }
        List<Long> fromLps = lpRepository.findAllById(lpIds).stream().map(LpEntity::getAlbumId).toList();
        return Stream.concat(cds.stream(), fromLps.stream()).distinct().sorted().toList();
    }

    private void validate(List<Line> lines) {
        if (lines.size() > props.maxItems()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "한 번에 최대 " + props.maxItems() + "개 상품까지 주문할 수 있습니다");
        }
        if (lines.stream().map(Line::productId).distinct().count() != lines.size()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "같은 상품이 두 번 들어 있습니다");
        }
        if (lines.stream().anyMatch(l -> l.qty() > props.maxQty())) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "상품당 최대 " + props.maxQty() + "개까지 주문할 수 있습니다");
        }
    }

    public record Line(long productId, int qty) {}

    private record Reserved(long orderId, int amount, List<Long> albumIds) {}
}
