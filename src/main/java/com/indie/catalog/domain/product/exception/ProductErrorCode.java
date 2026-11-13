package com.indie.catalog.domain.product.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 상품 · 옵션 · 재고 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum ProductErrorCode implements BaseErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다"),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다"),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다"),
    INVALID_OPTION(HttpStatus.BAD_REQUEST, "상품 종류에 맞지 않는 옵션입니다");   // 수집 적재 · 관리자 등록 — OptionRule

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
