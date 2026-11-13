package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderSummary;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.domain.product.dto.request.AdminOptionsRequest;
import com.indie.catalog.domain.product.dto.request.AdminProductCreate;
import com.indie.catalog.domain.product.dto.request.AdminProductUpdate;
import com.indie.catalog.domain.product.dto.request.AdminStockAdjust;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.dto.response.StockView;
import com.indie.catalog.domain.product.service.AdminProductService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.CursorPage;
import com.indie.catalog.global.common.util.Cursor;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자(Studio) 전용 내부 API — X-Internal-Token 의 iss 가 admin-issuers(studio)일 때만 (InternalTokenFilter).
 * 상품 · 옵션 · 재고(굿즈 관리자)와 회원 주문 조회 · 취소 대행(CS 관리자). 누가 했는지는 Studio 의 작업 기록에 남습니다.
 */
@RestController
public class InternalAdminCatalogController {

    private final AdminProductService adminProductService;
    private final OrderService orderService;
    private final PageSize pageSize;

    public InternalAdminCatalogController(AdminProductService adminProductService, OrderService orderService, PageSize pageSize) {
        this.adminProductService = adminProductService;
        this.orderService = orderService;
        this.pageSize = pageSize;
    }

    @PostMapping("/internal/admin/catalog/products")
    public ApiResponse<ProductDetail> create(@Valid @RequestBody AdminProductCreate request) {
        return ApiResponse.onSuccess(adminProductService.create(request));
    }

    @PatchMapping("/internal/admin/catalog/products/{id}")
    public ApiResponse<ProductDetail> update(@PathVariable @Positive long id, @Valid @RequestBody AdminProductUpdate request) {
        return ApiResponse.onSuccess(adminProductService.update(id, request));
    }

    @PutMapping("/internal/admin/catalog/products/{id}/options")
    public ApiResponse<ProductDetail> options(@PathVariable @Positive long id, @Valid @RequestBody AdminOptionsRequest request) {
        return ApiResponse.onSuccess(adminProductService.options(id, request.options()));
    }

    @PostMapping("/internal/admin/catalog/options/{optionId}/stock")
    public ApiResponse<StockView> adjustStock(@PathVariable @Positive long optionId, @Valid @RequestBody AdminStockAdjust request,
                                              @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        return ApiResponse.onSuccess(adminProductService.adjustStock(optionId, request.delta(), request.reason(), adminId));
    }

    /** 회원의 주문 목록 — 최신순 커서 (내 주문 목록 C10 과 같은 모양) */
    @GetMapping("/internal/admin/catalog/users/{userId}/orders")
    public ApiResponse<CursorPage<OrderSummary>> orders(@PathVariable @Positive long userId,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(orderService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /** 주문 취소 대행 — 결제 전이면 무효화, 결제 후면 환불까지 (C12 와 같음). 본문 { reason } 은 Studio 의 작업 기록용이라 여기서는 읽지 않습니다 */
    @PostMapping("/internal/admin/catalog/orders/{id}/cancel")
    public ApiResponse<OrderDetail> cancelOrder(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(orderService.cancelByAdmin(id));
    }
}
