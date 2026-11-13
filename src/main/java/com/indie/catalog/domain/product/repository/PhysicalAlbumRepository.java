package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.global.common.util.SourceHash;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 피지컬 앨범 저장소 포트 — 구현은 PhysicalAlbumRepositoryAdapter */
public interface PhysicalAlbumRepository {

    Optional<PhysicalAlbumEntity> findBySourceAndExternalId(String source, String externalId);

    /** 앨범들의 피지컬 앨범 — 발매일 최신순 */
    List<PhysicalAlbumEntity> findByAlbumIdIn(Collection<Long> albumIds);

    /** 발매 목록 — 매체 · 발매일 구간 모두 선택(null). 발매일 최신순 · 발매일 없는 항목은 뒤로 */
    List<PhysicalAlbumEntity> findReleased(PhysicalFormat format, LocalDate from, LocalDate to, long offset, int limit);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    PhysicalAlbumEntity save(PhysicalAlbumEntity physicalAlbum);
}
