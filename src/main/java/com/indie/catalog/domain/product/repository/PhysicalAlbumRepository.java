package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.SourceHash;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 피지컬 앨범 저장소 포트 - 구현은 PhysicalAlbumRepositoryAdapter */
public interface PhysicalAlbumRepository {

    Optional<PhysicalAlbumEntity> findBySourceAndExternalId(String source, String externalId);

    /** 앨범들의 피지컬 앨범 - 발매일 최신순 */
    List<PhysicalAlbumEntity> findByAlbumIdIn(Collection<Long> albumIds);

    /** 발매 목록 - 조건은 모두 선택(null, 빈 목록). sort 없으면 발매일 최신순, 발매일 없는 항목은 뒤로 */
    List<PhysicalAlbumEntity> search(Filter filter, long offset, int limit);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    PhysicalAlbumEntity save(PhysicalAlbumEntity physicalAlbum);

    /** formats, artistIds(앨범의 아티스트) 는 하나라도 맞으면, q 는 상품 이름, 앨범 제목, 아티스트 이름, 가격은 최저가(원) 구간 */
    /** excludeSoldOut - 품절 안 보기 (살 수 있는 옵션이 하나라도 있는 것만) */
    record Filter(List<PhysicalFormat> formats, LocalDate from, LocalDate to, List<Long> artistIds, String q,
                  Integer minPrice, Integer maxPrice, boolean excludeSoldOut, ListSort sort) {}
}
