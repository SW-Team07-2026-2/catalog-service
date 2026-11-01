package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AlbumJpaRepository extends JpaRepository<AlbumEntity, Long>, AlbumRepository {

    @Override
    @Query("select a from AlbumEntity a order by a.releasedAt desc nulls last, a.id desc")
    Slice<AlbumEntity> findLatest(Pageable pageable);

    @Override
    @Query("select a from AlbumEntity a where a.type = :type order by a.releasedAt desc nulls last, a.id desc")
    Slice<AlbumEntity> findLatestByType(@Param("type") AlbumEntity.Type type, Pageable pageable);

    @Override
    @Query("select new com.indie.catalog.global.common.util.SourceHash(a.externalId, a.sourceHash) "
            + "from AlbumEntity a where a.source = :source and a.externalId in :ids")
    List<SourceHash> findHashes(@Param("source") String source, @Param("ids") Collection<String> externalIds);
}
