package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.SourceHash;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 굿즈 저장소 포트 — 구현은 GoodsRepositoryAdapter */
public interface GoodsRepository {

    Optional<GoodsEntity> findBySourceAndExternalId(String source, String externalId);

    /** 목록 — 조건은 모두 선택(null · 빈 목록). sort 없으면 최신 등록순 */
    List<GoodsEntity> search(Filter filter, long offset, int limit);

    /** 한 아티스트의 굿즈 전부 — 최신 등록순 */
    List<GoodsEntity> findByArtistId(Long artistId);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    GoodsEntity save(GoodsEntity goods);

    /** artistIds · categories 는 하나라도 맞으면 · q 는 굿즈 이름 · 아티스트 이름 · 가격은 최저가(원) 구간 */
    record Filter(List<Long> artistIds, List<GoodsCategory> categories, String q, Integer minPrice, Integer maxPrice, ListSort sort) {}
}
