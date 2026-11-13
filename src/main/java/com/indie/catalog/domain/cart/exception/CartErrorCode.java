package com.indie.catalog.domain.cart.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 장바구니 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum CartErrorCode implements BaseErrorCode {

    CART_FULL(HttpStatus.CONFLICT, "장바구니가 가득 찼습니다");

    private final HttpStatus status;
    private final String message;

    CartErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getCode() { return name(); }

    @Override
    public String getMessage() { return message; }
}
