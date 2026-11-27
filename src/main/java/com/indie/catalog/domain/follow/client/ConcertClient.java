package com.indie.catalog.domain.follow.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Catalog -> Concert 공연 갱신 시각 조회 포트. 구현은 HttpConcertClient입니다.
 * 연결 실패, 5xx, 타임아웃은 Unavailable로 전달합니다.
 */
public interface ConcertClient {

    /**
     * 아티스트별 가장 최근 공연 등록, 수정 시각 - 공연이 없는 아티스트는 빠집니다
     *
     * @param artistIds 대상 아티스트 ID 목록
     */
    Map<Long, OffsetDateTime> latestConcertAt(List<Long> artistIds);

    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("concert latest failed: " + cause.getMessage(), cause);
        }
    }
}
