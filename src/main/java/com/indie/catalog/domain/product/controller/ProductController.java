package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
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

    @GetMapping("/api/catalog/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> physicalAlbums(
            @RequestParam(required = false) PhysicalFormat format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.physicalAlbums(format, from, to, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@RequestParam(required = false) @Positive Long artistId,
                                                    @RequestParam(required = false) GoodsCategory category,
                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.goods(artistId, category, page, pageSize.resolve(size)));
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
