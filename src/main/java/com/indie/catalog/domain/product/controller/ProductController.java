package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.ProductView;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공개 API C7 · C8 — 굿즈 · 판매 상품.
 * GET /api/catalog/products?ids= 는 12종 밖이지만 위시리스트 화면용 일괄 조회입니다 (08 §4 — 위시리스트가 productId 를 들고 있음).
 */
@RestController
public class ProductController {

    private final ProductService productService;
    private final PageSize pageSize;

    public ProductController(ProductService productService, PageSize pageSize) {
        this.productService = productService;
        this.pageSize = pageSize;
    }

    @GetMapping("/api/catalog/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@RequestParam(required = false) @Positive Long artistId,
                                                      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(productService.goods(artistId, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/products/{id}")
    public ApiResponse<ProductView> product(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(productService.product(id));
    }

    @GetMapping("/api/catalog/products")
    public ApiResponse<PageResult<ProductView>> products(@RequestParam List<Long> ids) {
        pageSize.checkIds(ids);
        return ApiResponse.onSuccess(PageResult.all(productService.products(ids)));
    }
}
