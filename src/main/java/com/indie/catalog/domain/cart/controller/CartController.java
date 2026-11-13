package com.indie.catalog.domain.cart.controller;

import com.indie.catalog.domain.cart.dto.request.CartPutRequest;
import com.indie.catalog.domain.cart.dto.response.CartView;
import com.indie.catalog.domain.cart.service.CartService;
import com.indie.catalog.global.auth.CurrentUser;
import com.indie.catalog.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 API — 장바구니 (Member 위시리스트 대체). 조회까지 모두 로그인 필요 (Gateway 가 X-User-Id 를 붙임 · 변경은 CSRF 도).
 * 변경 응답은 바뀐 뒤의 장바구니 전체입니다 — 프론트가 다시 조회하지 않아도 됩니다.
 */
@RestController
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/api/cart")
    public ApiResponse<CartView> get(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(cartService.get(userId));
    }

    /** 담기 · 수량 바꾸기 — qty 는 그 옵션의 최종 수량 */
    @PutMapping("/api/cart/items/{optionId}")
    public ApiResponse<CartView> put(@CurrentUser Long userId, @PathVariable @Positive long optionId,
                                     @Valid @RequestBody CartPutRequest request) {
        return ApiResponse.onSuccess(cartService.put(userId, optionId, request.qty()));
    }

    @DeleteMapping("/api/cart/items/{optionId}")
    public ApiResponse<CartView> remove(@CurrentUser Long userId, @PathVariable @Positive long optionId) {
        return ApiResponse.onSuccess(cartService.remove(userId, optionId));
    }

    /** 비우기 */
    @DeleteMapping("/api/cart")
    public ApiResponse<Void> clear(@CurrentUser Long userId) {
        cartService.clear(userId);
        return ApiResponse.onSuccess(null);
    }
}
