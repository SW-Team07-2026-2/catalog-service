package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.TrackEntity;

import java.util.List;

/** 트랙 저장소 포트 — 구현은 TrackJpaRepository */
public interface TrackRepository {

    List<TrackEntity> findByAlbumIdOrderByTrackNo(Long albumId);

    TrackEntity save(TrackEntity track);

    /** 수집 결과에서 빠진 번호의 트랙을 지웁니다 */
    void deleteAll(Iterable<? extends TrackEntity> tracks);
}
