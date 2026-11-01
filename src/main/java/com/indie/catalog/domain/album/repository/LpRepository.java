package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.LpEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** LP 저장소 포트 — 구현은 LpJpaRepository */
public interface LpRepository {

    List<LpEntity> findAllById(Iterable<Long> ids);

    Optional<LpEntity> findBySourceAndExternalId(String source, String externalId);

    List<LpEntity> findByAlbumIdInOrderByReleasedAtDescIdDesc(Collection<Long> albumIds);

    /** LP 발매 목록 (FR-CAT-12) — 발매일 구간 · 최신순 */
    Slice<LpEntity> findReleased(LocalDate from, LocalDate to, Pageable pageable);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    LpEntity save(LpEntity lp);
}
