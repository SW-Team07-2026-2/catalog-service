package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.domain.product.service.PublicProductCache;
import com.indie.catalog.global.auth.CurrentUserArgumentResolver;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.PageSize;
import com.indie.catalog.global.event.UserMetrics;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 프론트 -> Gateway -> Catalog의 피지컬 앨범, 굿즈, 상품 조회 API.
 * 공개 검색과 상세는 PublicProductCache를 사용하고 로그인한 회원의 검색어는 메트릭으로 기록합니다.
 */
@RestController
public class ProductController {

    private final ProductService productService;
    private final PublicProductCache publicCache;
    private final PageSize pageSize;
    private final UserMetrics userMetrics;

    public ProductController(ProductService productService, PublicProductCache publicCache, PageSize pageSize, UserMetrics userMetrics) {
        this.productService = productService;
        this.publicCache = publicCache;
        this.pageSize = pageSize;
        this.userMetrics = userMetrics;
    }

    /**
     * 매체, 발매일, 아티스트와 가격 조건으로 피지컬 앨범을 조회합니다.
     *
     * @param format 피지컬 앨범 매체 필터 목록
     * @param from 발매일 조회 시작일
     * @param to 발매일 조회 종료일
     * @param artistIds 대상 아티스트 ID 목록
     * @param q 검색어
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> physicalAlbums(
            @RequestParam(required = false) List<PhysicalFormat> format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> artistIds,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @PositiveOrZero Integer minPrice,
            @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
            @RequestParam(defaultValue = "false") boolean excludeSoldOut,
            @RequestParam(required = false) ListSort sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.physicalAlbums(format, from, to, artistIds, q, minPrice, maxPrice, excludeSoldOut, sort,
                page, pageSize.resolve(size)));
    }

    /**
     * 아티스트, 품목과 가격 조건으로 굿즈를 조회합니다.
     *
     * @param artistId 아티스트 ID
     * @param artistIds 대상 아티스트 ID 목록
     * @param category 굿즈 품목 필터 목록
     * @param q 검색어
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@RequestParam(required = false) @Positive Long artistId,
                                                    @RequestParam(required = false) List<Long> artistIds,
                                                    @RequestParam(required = false) List<GoodsCategory> category,
                                                    @RequestParam(required = false) @Size(max = 100) String q,
                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                    @RequestParam(defaultValue = "false") boolean excludeSoldOut,
                                                    @RequestParam(required = false) ListSort sort,
                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.goods(artistId, artistIds, category, q, minPrice, maxPrice, excludeSoldOut, sort,
                page, pageSize.resolve(size)));
    }

    /**
     * 공개 피지컬 앨범 검색 결과를 반환하고 회원 검색어를 기록합니다.
     *
     * @param q 검색어
     * @param format 피지컬 앨범 매체 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/api/catalog/search/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> searchPhysicalAlbums(@RequestParam(required = false) @Size(max = 100) String q,
                                                                          @RequestParam(required = false) List<PhysicalFormat> format,
                                                                          @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                                          @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                                          @RequestParam(defaultValue = "false") boolean excludeSoldOut,
                                                                          @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                                          @RequestParam(required = false) Integer size,
                                                                          @RequestHeader(value = CurrentUserArgumentResolver.HEADER, required = false) Long userId) {
        PageResult<PhysicalAlbumItem> result = publicCache.searchPhysicalAlbums(format, q, minPrice, maxPrice, excludeSoldOut, page,
                pageSize.resolve(size));   // Pod 캐시 3초 (PublicProductCache)
        userMetrics.searched(userId, "PHYSICAL_ALBUM", q, page);
        return ApiResponse.onSuccess(result);
    }

    /**
     * 공개 굿즈 검색 결과를 반환하고 회원 검색어를 기록합니다.
     *
     * @param q 검색어
     * @param category 굿즈 품목 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/api/catalog/search/goods")
    public ApiResponse<PageResult<GoodsItem>> searchGoods(@RequestParam(required = false) @Size(max = 100) String q,
                                                          @RequestParam(required = false) List<GoodsCategory> category,
                                                          @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                          @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                          @RequestParam(defaultValue = "false") boolean excludeSoldOut,
                                                          @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                          @RequestParam(required = false) Integer size,
                                                          @RequestHeader(value = CurrentUserArgumentResolver.HEADER, required = false) Long userId) {
        PageResult<GoodsItem> result = publicCache.searchGoods(category, q, minPrice, maxPrice, excludeSoldOut, page,
                pageSize.resolve(size));   // Pod 캐시 3초 (PublicProductCache)
        userMetrics.searched(userId, "GOODS", q, page);
        return ApiResponse.onSuccess(result);
    }

    /**
     * 상품 상세와 옵션별 가격, 재고를 조회합니다. 재고 표시는 캐시로 최대 3초 늦을 수 있습니다.
     *
     * @param productId 상품 ID
     */
    @GetMapping("/api/catalog/products/{productId}")
    public ApiResponse<ProductDetail> product(@PathVariable @Positive long productId) {
        return ApiResponse.onSuccess(publicCache.product(productId));   // Pod 캐시 3초 - 재고 수량, 품절은 최대 3초 늦음 (주문이 다시 확인)
    }

    /**
     * 보유 음반 화면과 챗봇에서 사용할 상품을 일괄 조회합니다.
     *
     * @param ids 상품 ID 목록
     */
    @GetMapping("/api/catalog/products")
    public ApiResponse<PageResult<ProductDetail>> products(@RequestParam List<Long> ids) {
        pageSize.checkIds(ids);
        return ApiResponse.onSuccess(PageResult.all(productService.products(ids)));
    }
}
