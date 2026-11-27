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
 * 공개 상품 검색과 상세 응답을 Pod 메모리에 3초간 캐시합니다.
 * 같은 키는 한 요청만 조회하며 404는 저장하지 않습니다. 주문 시 재고는 다시 확인합니다.
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

    /**
     * 캐시된 상품 상세를 반환하고 캐시가 없으면 DB에서 조회합니다.
     *
     * @param id 상품 ID
     */
    public ProductDetail product(long id) {
        return details.get(id, productService::product);
    }

    /**
     * 전체 검색 탭 - 굿즈 (최신 등록순)
     *
     * @param category 굿즈 품목 필터 목록
     * @param q 검색어
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    public PageResult<GoodsItem> searchGoods(List<GoodsCategory> category, String q, Integer minPrice, Integer maxPrice,
                                             boolean excludeSoldOut, int page, int size) {
        return goods.get(new Search(copy(category), q, minPrice, maxPrice, excludeSoldOut, page, size),
                k -> productService.goods(null, null, category, q, minPrice, maxPrice, excludeSoldOut, null, page, size));
    }

    /**
     * 전체 검색 탭 - 피지컬 (발매일 최신순)
     *
     * @param format 피지컬 앨범 매체 필터 목록
     * @param q 검색어
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    public PageResult<PhysicalAlbumItem> searchPhysicalAlbums(List<PhysicalFormat> format, String q, Integer minPrice, Integer maxPrice,
                                                              boolean excludeSoldOut, int page, int size) {
        return physicalAlbums.get(new Search(copy(format), q, minPrice, maxPrice, excludeSoldOut, page, size),
                k -> productService.physicalAlbums(format, null, null, null, q, minPrice, maxPrice, excludeSoldOut, null, page, size));
    }

    /** 칩(품목, 매체)은 고른 순서대로 키에 - 순서가 다르면 다른 키일 뿐 결과는 같습니다 */
    private record Search(List<? extends Enum<?>> chips, String q, Integer minPrice, Integer maxPrice, boolean excludeSoldOut,
                          int page, int size) {}

    private static List<? extends Enum<?>> copy(List<? extends Enum<?>> chips) {
        return chips == null ? null : List.copyOf(chips);
    }

    private static <K, V> Cache<K, V> cache() {
        return Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(1000).build();
    }
}
