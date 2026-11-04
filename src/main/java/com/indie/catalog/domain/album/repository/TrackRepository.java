package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.TrackEntity;

import java.util.List;

/** 트랙 저장소 포트 — 구현은 TrackJpaRepository */
public interface TrackRepository {

    List<TrackEntity> findAllById(Iterable<Long> ids);

    List<TrackEntity> findByAlbumIdOrderByTrackNo(Long albumId);

    void deleteByAlbumId(Long albumId);

    TrackEntity save(TrackEntity track);
}
