package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 공개 API C6 · C7 · C8 — 피지컬 앨범 · 굿즈 · 상품(옵션별 가격 · 재고).
 * 피지컬 앨범과 굿즈는 상품 id 를 공유하므로 상세는 /products/{id} 하나입니다.
 */
@RestController
public class ProductController {

    private final ProductService productService;
    private final PageSize pageSize;

    public ProductController(ProductService productService, PageSize pageSize) {
        this.productService = productService;
        this.pageSize = pageSize;
    }

    /** 11/19 팔로우 탭 — format · artistIds 는 여러 개(format=LP&format=CD 또는 format=LP,CD) · 가격은 최저가(원) · sort 없으면 발매일 최신순 */
    @GetMapping("/api/catalog/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> physicalAlbums(
            @RequestParam(required = false) List<PhysicalFormat> format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<Long> artistIds,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @PositiveOrZero Integer minPrice,
            @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
            @RequestParam(required = false) ListSort sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.physicalAlbums(format, from, to, artistIds, q, minPrice, maxPrice, sort,
                page, pageSize.resolve(size)));
    }

    /** 11/19 팔로우 탭 — category · artistIds 는 여러 개 · artistId(하나)는 기존 그대로 · 가격은 최저가(원) · sort 없으면 최신 등록순 */
    @GetMapping("/api/catalog/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@RequestParam(required = false) @Positive Long artistId,
                                                    @RequestParam(required = false) List<Long> artistIds,
                                                    @RequestParam(required = false) List<GoodsCategory> category,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                    @RequestParam(required = false) ListSort sort,
                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.goods(artistId, artistIds, category, q, minPrice, maxPrice, sort,
                page, pageSize.resolve(size)));
    }

    /** 11/19 전체 검색 (프론트 요구 1-3 · 2-3) — 상품명 · 앨범 제목 · 아티스트 이름 LIKE + 매체(여러 개) + 가격대. q 가 없으면 칩만으로 · 발매일 최신순 */
    @GetMapping("/api/catalog/search/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> searchPhysicalAlbums(@RequestParam(required = false) String q,
                                                                          @RequestParam(required = false) List<PhysicalFormat> format,
                                                                          @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                                          @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                                          @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.physicalAlbums(format, null, null, null, q, minPrice, maxPrice, null,
                page, pageSize.resolve(size)));
    }

    /** 11/19 전체 검색 (프론트 요구 1-3 · 2-4) — 굿즈명 · 아티스트 이름 LIKE + 품목(여러 개) + 가격대. q 가 없으면 칩만으로 · 최신 등록순 */
    @GetMapping("/api/catalog/search/goods")
    public ApiResponse<PageResult<GoodsItem>> searchGoods(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) List<GoodsCategory> category,
                                                          @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                          @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                          @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.goods(null, null, category, q, minPrice, maxPrice, null,
                page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/products/{id}")
    public ApiResponse<ProductDetail> product(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(productService.product(id));
    }

    /** ?ids=1,2,3 — 보유 음반 화면 · 챗봇 도구의 일괄 조회 */
    @GetMapping("/api/catalog/products")
    public ApiResponse<PageResult<ProductDetail>> products(@RequestParam List<Long> ids) {
        pageSize.checkIds(ids);
        return ApiResponse.onSuccess(PageResult.all(productService.products(ids)));
    }
}
