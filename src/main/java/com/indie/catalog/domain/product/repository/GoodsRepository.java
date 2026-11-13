package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.global.common.util.SourceHash;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 굿즈 저장소 포트 — 구현은 GoodsRepositoryAdapter */
public interface GoodsRepository {

    Optional<GoodsEntity> findBySourceAndExternalId(String source, String externalId);

    /** 최신 등록순 — 아티스트 · 품목 모두 선택(null) */
    List<GoodsEntity> findLatest(Long artistId, GoodsCategory category, long offset, int limit);

    /** 한 아티스트의 굿즈 전부 — 최신 등록순 */
    List<GoodsEntity> findByArtistId(Long artistId);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    GoodsEntity save(GoodsEntity goods);
}
