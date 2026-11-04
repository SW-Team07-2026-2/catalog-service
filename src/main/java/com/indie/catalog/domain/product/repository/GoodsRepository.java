package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 굿즈 저장소 포트 — 구현은 GoodsJpaRepository */
public interface GoodsRepository {

    Optional<GoodsEntity> findBySourceAndExternalId(String source, String externalId);

    List<GoodsEntity> findByArtistIdOrderById(Long artistId);

    /** 최신 등록순 */
    Slice<GoodsEntity> findLatest(Pageable pageable);

    Slice<GoodsEntity> findLatestByArtist(Long artistId, Pageable pageable);

    List<SourceHash> findHashes(String source, Collection<String> externalIds);

    GoodsEntity save(GoodsEntity goods);
}
