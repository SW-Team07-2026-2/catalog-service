package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.album.entity.QAlbumEntity.albumEntity;

/** AlbumRepository 구현 — 단건 · 저장은 Spring Data, 목록 · 해시 조회는 QueryDSL */
@Repository
public class AlbumRepositoryAdapter implements AlbumRepository {

    private final AlbumJpaRepository jpa;
    private final JPAQueryFactory query;

    public AlbumRepositoryAdapter(AlbumJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public Optional<AlbumEntity> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<AlbumEntity> findAllById(Iterable<Long> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public Optional<AlbumEntity> findBySourceAndExternalId(String source, String externalId) {
        return jpa.findBySourceAndExternalId(source, externalId);
    }

    @Override
    public List<AlbumEntity> findLatest(AlbumEntity.Type type, long offset, int limit) {
        return query.selectFrom(albumEntity)
                .where(type == null ? null : albumEntity.type.eq(type))
                .orderBy(albumEntity.releasedAt.desc().nullsLast(), albumEntity.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    @Override
    public List<AlbumEntity> findByArtistId(Long artistId) {
        return query.selectFrom(albumEntity)
                .where(albumEntity.artistId.eq(artistId))
                .orderBy(albumEntity.releasedAt.desc().nullsLast(), albumEntity.id.desc())
                .fetch();
    }

    @Override
    public List<SourceHash> findHashes(String source, Collection<String> externalIds) {
        return query.select(Projections.constructor(SourceHash.class, albumEntity.externalId, albumEntity.sourceHash))
                .from(albumEntity)
                .where(albumEntity.source.eq(source), albumEntity.externalId.in(externalIds))
                .fetch();
    }

    @Override
    public AlbumEntity save(AlbumEntity album) {
        return jpa.save(album);
    }
}
