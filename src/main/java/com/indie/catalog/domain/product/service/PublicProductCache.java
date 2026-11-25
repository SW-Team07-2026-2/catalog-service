package com.indie.catalog.domain.product.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * ✏️ 11/25 k6 S6 3,000명 — 굿즈 오픈 때 모두가 같은 목록(전체 검색 탭) · 같은 상품 상세를 부릅니다. Pod 1개 · CPU 0.8코어에서
 * CPU 스로틀 78% · 사용자가 받는 시간 3~5초 → Gateway 서킷이 열려 장바구니 · 주문까지 503 이었습니다.
 * 결과를 Pod 메모리에 3초 둡니다 — 비면 한 요청만 DB 로 가고 같은 키의 나머지는 그 결과를 기다립니다.
 * 화면의 재고 수량 · 품절 표시는 최대 3초 늦을 수 있습니다 (주문은 재고를 다시 확인 · 조건부 차감).
 * ProductService 는 트랜잭션을 열 때 DB 연결을 잡으므로 그 밖(컨트롤러 쪽)에서 캐시를 먼저 봅니다. 없는 상품(404)은 남기지 않습니다
 */
@Component
public class PublicProductCache {

    static final Duration TTL = Duration.ofSeconds(3);

    private final ProductService productService;
    private final Cache<Long, ProductDetail> details = cache();
    private final Cache<Search, PageResult<GoodsItem>> goods = cache();
    private final Cache<Search, PageResult<PhysicalAlbumItem>> physicalAlbums = cache();

    public PublicProductCache(ProductService productService) {
        this.productService = productService;
    }

    public ProductDetail product(long id) {
        return details.get(id, productService::product);
    }

    /** 전체 검색 탭 — 굿즈 (최신 등록순) */
    public PageResult<GoodsItem> searchGoods(List<GoodsCategory> category, String q, Integer minPrice, Integer maxPrice,
                                             boolean excludeSoldOut, int page, int size) {
        return goods.get(new Search(copy(category), q, minPrice, maxPrice, excludeSoldOut, page, size),
                k -> productService.goods(null, null, category, q, minPrice, maxPrice, excludeSoldOut, null, page, size));
    }

    /** 전체 검색 탭 — 피지컬 (발매일 최신순) */
    public PageResult<PhysicalAlbumItem> searchPhysicalAlbums(List<PhysicalFormat> format, String q, Integer minPrice, Integer maxPrice,
                                                              boolean excludeSoldOut, int page, int size) {
        return physicalAlbums.get(new Search(copy(format), q, minPrice, maxPrice, excludeSoldOut, page, size),
                k -> productService.physicalAlbums(format, null, null, null, q, minPrice, maxPrice, excludeSoldOut, null, page, size));
    }

    /** 칩(품목 · 매체)은 고른 순서대로 키에 — 순서가 다르면 다른 키일 뿐 결과는 같습니다 */
    private record Search(List<? extends Enum<?>> chips, String q, Integer minPrice, Integer maxPrice, boolean excludeSoldOut,
                          int page, int size) {}

    private static List<? extends Enum<?>> copy(List<? extends Enum<?>> chips) {
        return chips == null ? null : List.copyOf(chips);
    }

    private static <K, V> Cache<K, V> cache() {
        return Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(1000).build();
    }
}
