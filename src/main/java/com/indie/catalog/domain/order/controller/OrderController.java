package com.indie.catalog.domain.order.controller;

import com.indie.catalog.domain.order.dto.request.OrderCreateRequest;
import com.indie.catalog.domain.order.dto.response.OrderCreated;
import com.indie.catalog.domain.order.dto.response.OrderDetail;
import com.indie.catalog.domain.order.dto.response.OrderSummary;
import com.indie.catalog.domain.order.service.OrderService;
import com.indie.catalog.global.auth.CurrentUser;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.CursorPage;
import com.indie.catalog.global.common.util.Cursor;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 프론트 -> Gateway -> Catalog OrderService의 주문 생성, 조회, 취소 API. */
@RestController
public class OrderController {

    private final OrderService orderService;
    private final PageSize pageSize;

    public OrderController(OrderService orderService, PageSize pageSize) {
        this.orderService = orderService;
        this.pageSize = pageSize;
    }

    /**
     * 옵션 가격으로 주문 금액을 계산하고 결제를 준비합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 주문할 옵션, 수량과 배송 정보
     */
    @PostMapping("/api/catalog/orders")
    public ApiResponse<OrderCreated> create(@CurrentUser Long userId, @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.onSuccess(orderService.create(userId, request.items().stream()
                .map(i -> new OrderService.Line(i.optionId(), i.qty())).toList(), request.shipping().toSnapshot()));
    }

    /**
     * 회원 본인의 주문 목록을 최신순으로 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/orders")
    public ApiResponse<CursorPage<OrderSummary>> list(@CurrentUser Long userId,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(orderService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /**
     * 회원 본인의 주문 상세를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param orderId Catalog 주문 ID
     */
    @GetMapping("/api/catalog/orders/{orderId}")
    public ApiResponse<OrderDetail> get(@CurrentUser Long userId, @PathVariable @Positive long orderId) {
        return ApiResponse.onSuccess(orderService.get(userId, orderId));
    }

    /**
     * 결제를 취소한 뒤 주문 상태와 재고를 변경합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param orderId Catalog 주문 ID
     */
    @PostMapping("/api/catalog/orders/{orderId}/cancel")
    public ApiResponse<OrderDetail> cancel(@CurrentUser Long userId, @PathVariable @Positive long orderId) {
        return ApiResponse.onSuccess(orderService.cancel(userId, orderId));
    }
}
