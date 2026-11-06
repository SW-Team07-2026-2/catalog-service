package com.indie.catalog.domain.search.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 검색 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum SearchErrorCode implements BaseErrorCode {

    SEARCH_QUERY_EMPTY(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요"),
    SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "검색어는 50자까지입니다");

    private final HttpStatus status;
    private final String message;

    SearchErrorCode(HttpStatus status, String message) {
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
