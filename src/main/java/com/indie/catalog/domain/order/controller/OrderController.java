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

/** 공개 API C9 ~ C12 — 주문. 모두 로그인 필요 (Gateway 가 X-User-Id 를 붙임 · POST 는 CSRF 도) */
@RestController
public class OrderController {

    private final OrderService orderService;
    private final PageSize pageSize;

    public OrderController(OrderService orderService, PageSize pageSize) {
        this.orderService = orderService;
        this.pageSize = pageSize;
    }

    /** 🔴 요청에 금액 필드가 없습니다 — 있어도 무시됩니다 (서버가 옵션 가격으로 계산) */
    @PostMapping("/api/catalog/orders")
    public ApiResponse<OrderCreated> create(@CurrentUser Long userId, @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.onSuccess(orderService.create(userId, request.items().stream()
                .map(i -> new OrderService.Line(i.optionId(), i.qty())).toList(), request.shipping().toSnapshot()));
    }

    @GetMapping("/api/catalog/orders")
    public ApiResponse<CursorPage<OrderSummary>> list(@CurrentUser Long userId,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(orderService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/orders/{id}")
    public ApiResponse<OrderDetail> get(@CurrentUser Long userId, @PathVariable @Positive long id) {
        return ApiResponse.onSuccess(orderService.get(userId, id));
    }

    @PostMapping("/api/catalog/orders/{id}/cancel")
    public ApiResponse<OrderDetail> cancel(@CurrentUser Long userId, @PathVariable @Positive long id) {
        return ApiResponse.onSuccess(orderService.cancel(userId, id));
    }
}
