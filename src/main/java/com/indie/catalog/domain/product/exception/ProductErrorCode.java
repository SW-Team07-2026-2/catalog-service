package com.indie.catalog.domain.product.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 상품 · 재고 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum ProductErrorCode implements BaseErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다");

    private final HttpStatus status;
    private final String message;

    ProductErrorCode(HttpStatus status, String message) {
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
