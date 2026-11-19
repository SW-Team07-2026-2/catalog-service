package com.indie.catalog.domain.follow.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Concert 호출 포트 (11/19 팔로우 탭 업데이트순) — 구현은 HttpConcertClient (내부 토큰 iss=catalog · 타임아웃 3초).
 * 불통 · 5xx · 타임아웃은 전부 Unavailable.
 */
public interface ConcertClient {

    /** 아티스트별 가장 최근 공연 등록 · 수정 시각 — 공연이 없는 아티스트는 빠집니다 */
    Map<Long, OffsetDateTime> latestConcertAt(List<Long> artistIds);

    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("concert latest failed: " + cause.getMessage(), cause);
        }
    }
}
