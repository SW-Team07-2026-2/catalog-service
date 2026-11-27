package com.indie.catalog.domain.cart.service;

import com.indie.catalog.domain.cart.dto.response.CartLine;
import com.indie.catalog.domain.cart.dto.response.CartView;
import com.indie.catalog.domain.cart.entity.CartItemEntity;
import com.indie.catalog.domain.cart.exception.CartErrorCode;
import com.indie.catalog.domain.cart.repository.CartItemRepository;
import com.indie.catalog.domain.product.entity.StockEntity;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.domain.product.repository.ProductOptionRepository;
import com.indie.catalog.domain.product.repository.StockRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.config.CatalogProperties;
import com.indie.catalog.global.event.UserMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 옵션별 장바구니 수량을 관리하고 사용자 변경을 행동 메트릭으로 기록합니다.
 * 재고는 조회만 하며 주문 생성 시 차감합니다. 주문 확정 시 해당 옵션을, 탈퇴 시 전체를 삭제합니다.
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository optionRepository;
    private final StockRepository stockRepository;
    private final ProductService productService;
    private final UserMetrics userMetrics;
    private final CatalogProperties.Order limits;
    private final Clock clock;

    public CartService(CartItemRepository cartItemRepository, ProductOptionRepository optionRepository,
                       StockRepository stockRepository, ProductService productService, UserMetrics userMetrics,
                       CatalogProperties props, Clock clock) {
        this.cartItemRepository = cartItemRepository;
        this.optionRepository = optionRepository;
        this.stockRepository = stockRepository;
        this.productService = productService;
        this.userMetrics = userMetrics;
        this.limits = props.order();
        this.clock = clock;
    }

    /**
     * 회원의 장바구니와 현재 가격, 재고를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional(readOnly = true)
    public CartView get(long userId) {
        List<CartItemEntity> items = cartItemRepository.findByUserId(userId);
        List<Long> optionIds = items.stream().map(CartItemEntity::getOptionId).toList();
        Map<Long, ProductService.Sellable> sellables = productService.sellables(optionIds);
        Map<Long, Integer> stocks = stockRepository.findAllById(optionIds).stream()
                .collect(Collectors.toMap(StockEntity::getOptionId, StockEntity::getQty));
        int total = 0;
        List<CartLine> lines = new ArrayList<>();
        for (CartItemEntity item : items) {
            ProductService.Sellable s = sellables.get(item.getOptionId());   // cart_item.option_id 는 FK - 항상 있습니다
            int stockQty = stocks.getOrDefault(item.getOptionId(), 0);
            lines.add(new CartLine(item.getOptionId(), s.product().getId(), s.product().getProductType(), s.product().getName(),
                    s.option().getName(), s.option().getVinylColor(), s.option().getSize(), s.product().getThumbnailUrl(),
                    s.option().getPrice(), item.getQty(), stockQty, stockQty == 0, item.getCreatedAt()));
            total = Math.addExact(total, Math.multiplyExact(s.option().getPrice(), item.getQty()));
        }
        return new CartView(lines, total);
    }

    /**
     * 담기, 수량 바꾸기 - 같은 요청을 다시 보내도 결과가 같습니다 (수량은 더하지 않고 덮어씀)
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param optionId 상품 옵션 ID
     * @param qty 설정할 수량
     */
    @Transactional
    public CartView put(long userId, long optionId, int qty) {
        if (qty > limits.maxQty()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "상품당 최대 " + limits.maxQty() + "개까지 담을 수 있습니다");
        }
        if (optionRepository.findById(optionId).isEmpty()) throw new GeneralException(ProductErrorCode.OPTION_NOT_FOUND);
        List<CartItemEntity> items = cartItemRepository.findByUserId(userId);
        if (items.size() >= limits.maxItems() && items.stream().noneMatch(i -> i.getOptionId() == optionId)) {
            throw new GeneralException(CartErrorCode.CART_FULL, "장바구니에는 최대 " + limits.maxItems() + "개 상품까지 담을 수 있습니다");
        }
        int before = items.stream().filter(i -> i.getOptionId() == optionId).mapToInt(CartItemEntity::getQty).findFirst().orElse(0);
        cartItemRepository.put(userId, optionId, qty, OffsetDateTime.now(clock));
        CartView after = get(userId);
        after.items().stream().filter(l -> l.optionId() == optionId).findFirst().ifPresent(l ->
                userMetrics.cartChanged(userId, l.productType().name(), l.productId(), optionId, before, qty));
        return after;
    }

    /**
     * 담겨 있지 않아도 성공 - 두 번 눌러도 결과가 같습니다
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param optionId 상품 옵션 ID
     */
    @Transactional
    public CartView remove(long userId, long optionId) {
        List<CartLine> removed = get(userId).items().stream().filter(l -> l.optionId() == optionId).toList();
        cartItemRepository.delete(userId, optionId);
        removed.forEach(l -> removedMetric(userId, l));
        return get(userId);
    }

    /**
     * 회원이 비우기 (DELETE /api/cart) - 뺀 수량을 메트릭으로 남깁니다
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional
    public void empty(long userId) {
        List<CartLine> removed = get(userId).items();
        cartItemRepository.deleteByUserId(userId);
        removed.forEach(l -> removedMetric(userId, l));
    }

    /**
     * 탈퇴 정리 (user.withdrawn) - 회원이 뺀 것이 아니라 메트릭을 남기지 않습니다
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional
    public void clear(long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    private void removedMetric(long userId, CartLine line) {
        userMetrics.cartChanged(userId, line.productType().name(), line.productId(), line.optionId(), line.qty(), 0);
    }
}
