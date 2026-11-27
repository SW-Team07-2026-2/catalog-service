package com.indie.catalog.domain.product.controller;

import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderSummary;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.domain.product.dto.request.AdminOptionsRequest;
import com.indie.catalog.domain.product.dto.request.AdminProductCreate;
import com.indie.catalog.domain.product.dto.request.AdminProductUpdate;
import com.indie.catalog.domain.product.dto.request.AdminStockAdjust;
import com.indie.catalog.domain.product.dto.request.AdminStockSet;
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
 * Studio -> Catalog 내부 관리자 API -> AdminProductService와 OrderService.
 * 상품, 옵션, 재고 관리와 회원 주문 조회, 취소 대행을 처리합니다.
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

    /**
     * 관리자 상품을 등록합니다.
     *
     * @param request 등록할 상품 정보
     */
    @PostMapping("/internal/admin/catalog/products")
    public ApiResponse<ProductDetail> create(@Valid @RequestBody AdminProductCreate request) {
        return ApiResponse.onSuccess(adminProductService.create(request));
    }

    /**
     * 상품 정보를 수정합니다.
     *
     * @param productId 상품 ID
     * @param request 수정할 상품 정보
     */
    @PatchMapping("/internal/admin/catalog/products/{productId}")
    public ApiResponse<ProductDetail> update(@PathVariable @Positive long productId, @Valid @RequestBody AdminProductUpdate request) {
        return ApiResponse.onSuccess(adminProductService.update(productId, request));
    }

    /**
     * 상품 옵션을 추가하거나 수정합니다.
     *
     * @param productId 상품 ID
     * @param request 추가하거나 수정할 옵션 목록
     */
    @PutMapping("/internal/admin/catalog/products/{productId}/options")
    public ApiResponse<ProductDetail> options(@PathVariable @Positive long productId, @Valid @RequestBody AdminOptionsRequest request) {
        return ApiResponse.onSuccess(adminProductService.options(productId, request.options()));
    }

    /**
     * 옵션 재고를 증감합니다.
     *
     * @param optionId 상품 옵션 ID
     * @param request 재고 증감량과 변경 사유
     * @param adminId 작업을 수행한 관리자 ID
     */
    @PostMapping("/internal/admin/catalog/options/{optionId}/stock")
    public ApiResponse<StockView> adjustStock(@PathVariable @Positive long optionId, @Valid @RequestBody AdminStockAdjust request,
                                              @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        return ApiResponse.onSuccess(adminProductService.adjustStock(optionId, request.delta(), request.reason(), adminId));
    }

    /**
     * 옵션 재고를 지정 수량으로 설정합니다.
     *
     * @param optionId 상품 옵션 ID
     * @param request 설정할 재고 수량과 변경 사유
     * @param adminId 작업을 수행한 관리자 ID
     */
    @PutMapping("/internal/admin/catalog/options/{optionId}/stock")
    public ApiResponse<StockView> setStock(@PathVariable @Positive long optionId, @Valid @RequestBody AdminStockSet request,
                                           @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        return ApiResponse.onSuccess(adminProductService.setStock(optionId, request.qty(), request.reason(), adminId));
    }

    /**
     * 지정한 회원의 주문 목록을 최신순으로 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/internal/admin/catalog/users/{userId}/orders")
    public ApiResponse<CursorPage<OrderSummary>> orders(@PathVariable @Positive long userId,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(orderService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /**
     * 회원을 대신해 주문을 취소하고 필요한 환불을 처리합니다.
     *
     * @param orderId Catalog 주문 ID
     */
    @PostMapping("/internal/admin/catalog/orders/{orderId}/cancel")
    public ApiResponse<OrderDetail> cancelOrder(@PathVariable @Positive long orderId) {
        return ApiResponse.onSuccess(orderService.cancelByAdmin(orderId));
    }
}
