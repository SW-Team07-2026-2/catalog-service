package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GoodsJpaRepository extends JpaRepository<GoodsEntity, Long>, GoodsRepository {

    @Override
    @Query("select g from GoodsEntity g order by g.id desc")
    Slice<GoodsEntity> findLatest(Pageable pageable);

    @Override
    @Query("select g from GoodsEntity g where g.artistId = :artistId order by g.id desc")
    Slice<GoodsEntity> findLatestByArtist(@Param("artistId") Long artistId, Pageable pageable);

    @Override
    @Query("select new com.indie.catalog.global.common.util.SourceHash(g.externalId, g.sourceHash) "
            + "from GoodsEntity g where g.source = :source and g.externalId in :ids")
    List<SourceHash> findHashes(@Param("source") String source, @Param("ids") Collection<String> externalIds);
}
