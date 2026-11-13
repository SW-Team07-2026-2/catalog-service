package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.product.entity.QPhysicalAlbumEntity.physicalAlbumEntity;

/** PhysicalAlbumRepository 구현 — 단건 · 저장은 Spring Data, 목록 · 해시 조회는 QueryDSL */
@Repository
public class PhysicalAlbumRepositoryAdapter implements PhysicalAlbumRepository {

    private final PhysicalAlbumJpaRepository jpa;
    private final JPAQueryFactory query;

    public PhysicalAlbumRepositoryAdapter(PhysicalAlbumJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public Optional<PhysicalAlbumEntity> findBySourceAndExternalId(String source, String externalId) {
        return jpa.findBySourceAndExternalId(source, externalId);
    }

    @Override
    public List<PhysicalAlbumEntity> findByAlbumIdIn(Collection<Long> albumIds) {
        return query.selectFrom(physicalAlbumEntity)
                .where(physicalAlbumEntity.albumId.in(albumIds))
                .orderBy(physicalAlbumEntity.releasedAt.desc().nullsLast(), physicalAlbumEntity.id.desc())
                .fetch();
    }

    @Override
    public List<PhysicalAlbumEntity> findReleased(PhysicalFormat format, LocalDate from, LocalDate to, long offset, int limit) {
        return query.selectFrom(physicalAlbumEntity)
                .where(format == null ? null : physicalAlbumEntity.format.eq(format),
                        from == null ? null : physicalAlbumEntity.releasedAt.goe(from),
                        to == null ? null : physicalAlbumEntity.releasedAt.loe(to))
                .orderBy(physicalAlbumEntity.releasedAt.desc().nullsLast(), physicalAlbumEntity.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    @Override
    public List<SourceHash> findHashes(String source, Collection<String> externalIds) {
        return query.select(Projections.constructor(SourceHash.class, physicalAlbumEntity.externalId, physicalAlbumEntity.sourceHash))
                .from(physicalAlbumEntity)
                .where(physicalAlbumEntity.source.eq(source), physicalAlbumEntity.externalId.in(externalIds))
                .fetch();
    }

    @Override
    public PhysicalAlbumEntity save(PhysicalAlbumEntity physicalAlbum) {
        return jpa.save(physicalAlbum);
    }
}
