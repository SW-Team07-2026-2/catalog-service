package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.dto.response.ArtistDetail;

import java.util.Optional;

/**
 * 아티스트 상세 캐시 포트 — catalog:cache:artist:{id} · TTL 10분 · 수정 시 삭제 (08 §3). 구현은 RedisArtistCache.
 * 🔴 구현은 모든 실패를 삼켜야 합니다 — 캐시가 죽어도 DB 로 응답합니다 (fail-open). 재고는 담지 않습니다.
 */
public interface ArtistCache {

    Optional<ArtistDetail> get(long id);

    void put(ArtistDetail detail);

    /** 커밋 뒤에 지웁니다 — 커밋 전에 지우면 그 사이 다른 요청이 옛 값을 다시 채웁니다 */
    void evictAfterCommit(long id);
}
