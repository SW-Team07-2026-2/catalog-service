package com.indie.catalog.domain.order.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 주문 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum OrderErrorCode implements BaseErrorCode {

    PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제를 준비하지 못했습니다. 잠시 후 다시 시도해주세요");

    private final HttpStatus status;
    private final String message;

    OrderErrorCode(HttpStatus status, String message) {
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
