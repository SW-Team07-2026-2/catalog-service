package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.LpEntity;
import com.indie.catalog.global.common.util.SourceHash;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LpJpaRepository extends JpaRepository<LpEntity, Long>, LpRepository {

    @Override
    @Query("""
            select l from LpEntity l
            where l.releasedAt >= :from and l.releasedAt <= :to
            order by l.releasedAt desc, l.id desc
            """)
    Slice<LpEntity> findReleased(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Override
    @Query("select new com.indie.catalog.global.common.util.SourceHash(l.externalId, l.sourceHash) "
            + "from LpEntity l where l.source = :source and l.externalId in :ids")
    List<SourceHash> findHashes(@Param("source") String source, @Param("ids") Collection<String> externalIds);
}
