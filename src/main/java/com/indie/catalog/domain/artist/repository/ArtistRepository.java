package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.global.common.util.SourceHash;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 아티스트 저장소 포트 — 구현은 ArtistRepositoryAdapter. (source, external_id) 가 수집 멱등 upsert 기준 */
public interface ArtistRepository {

    Optional<ArtistEntity> findById(Long id);

    List<ArtistEntity> findAllById(Iterable<Long> ids);

    Optional<ArtistEntity> findBySourceAndExternalId(String source, String externalId);

    /** 이름 부분 일치(대소문자 무시) · 장르 포함 — 둘 다 선택(null). 이름순 */
    List<ArtistEntity> search(String nameContains, Genre genre, long offset, int limit);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    ArtistEntity save(ArtistEntity artist);
}
