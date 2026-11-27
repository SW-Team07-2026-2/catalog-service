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
 * 로그인한 프론트 -> Gateway -> Catalog CartService의 장바구니 API.
 * 변경 후 장바구니 전체를 반환합니다.
 */
@RestController
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * 회원의 장바구니를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/api/cart")
    public ApiResponse<CartView> get(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(cartService.get(userId));
    }

    /**
     * 옵션 수량을 설정하고 장바구니를 반환합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param optionId 상품 옵션 ID
     * @param request 장바구니에 설정할 수량
     */
    @PutMapping("/api/cart/items/{optionId}")
    public ApiResponse<CartView> put(@CurrentUser Long userId, @PathVariable @Positive long optionId,
                                     @Valid @RequestBody CartPutRequest request) {
        return ApiResponse.onSuccess(cartService.put(userId, optionId, request.qty()));
    }

    /**
     * 옵션을 삭제하고 장바구니를 반환합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param optionId 상품 옵션 ID
     */
    @DeleteMapping("/api/cart/items/{optionId}")
    public ApiResponse<CartView> remove(@CurrentUser Long userId, @PathVariable @Positive long optionId) {
        return ApiResponse.onSuccess(cartService.remove(userId, optionId));
    }

    /**
     * 장바구니를 비우고 결과를 반환합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @DeleteMapping("/api/cart")
    public ApiResponse<Void> clear(@CurrentUser Long userId) {
        cartService.empty(userId);
        return ApiResponse.onSuccess(null);
    }
}
