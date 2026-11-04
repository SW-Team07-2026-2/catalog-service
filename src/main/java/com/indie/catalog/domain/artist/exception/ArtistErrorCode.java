package com.indie.catalog.domain.artist.exception;

import com.indie.catalog.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 아티스트 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum ArtistErrorCode implements BaseErrorCode {

    ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다");

    private final HttpStatus status;
    private final String message;

    ArtistErrorCode(HttpStatus status, String message) {
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
