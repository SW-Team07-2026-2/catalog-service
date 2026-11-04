package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.product.dto.response.ProductView;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.PageSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내부 I6 — 어시스턴트 도구의 벌크 조회 (X-Internal-Token · Gateway 라우트 없음) */
@RestController
public class InternalProductController {

    private final ProductService productService;
    private final PageSize pageSize;

    public InternalProductController(ProductService productService, PageSize pageSize) {
        this.productService = productService;
        this.pageSize = pageSize;
    }

    @GetMapping("/internal/catalog/products")
    public ApiResponse<PageResult<ProductView>> products(@RequestParam List<Long> ids) {
        pageSize.checkIds(ids);
        return ApiResponse.onSuccess(PageResult.all(productService.products(ids)));
    }
}
