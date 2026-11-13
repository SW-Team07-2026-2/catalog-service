package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.global.common.util.SourceHash;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 앨범 저장소 포트 — 구현은 AlbumRepositoryAdapter */
public interface AlbumRepository {

    Optional<AlbumEntity> findById(Long id);

    List<AlbumEntity> findAllById(Iterable<Long> ids);

    Optional<AlbumEntity> findBySourceAndExternalId(String source, String externalId);

    /** 신보순 — 발매일 없는 항목은 뒤로. type 은 선택(null) */
    List<AlbumEntity> findLatest(AlbumEntity.Type type, long offset, int limit);

    /** 한 아티스트의 앨범 — 신보순 */
    List<AlbumEntity> findByArtistId(Long artistId);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    AlbumEntity save(AlbumEntity album);
}
