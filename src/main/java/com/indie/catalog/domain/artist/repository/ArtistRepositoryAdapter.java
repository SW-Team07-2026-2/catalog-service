package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.global.common.util.LikePattern;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.artist.entity.QArtistEntity.artistEntity;

/** ArtistRepository 구현 — 단건 · 저장은 Spring Data, 목록 필터 · 해시 조회는 QueryDSL */
@Repository
public class ArtistRepositoryAdapter implements ArtistRepository {

    private final ArtistJpaRepository jpa;
    private final JPAQueryFactory query;

    public ArtistRepositoryAdapter(ArtistJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public Optional<ArtistEntity> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<ArtistEntity> findAllById(Iterable<Long> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public Optional<ArtistEntity> findBySourceAndExternalId(String source, String externalId) {
        return jpa.findBySourceAndExternalId(source, externalId);
    }

    /** 이름은 idx_artist_name_trgm (lower(name)), 장르는 idx_artist_genres (genres @> …) 를 탑니다 */
    @Override
    public List<ArtistEntity> search(String nameContains, Genre genre, long offset, int limit) {
        return query.selectFrom(artistEntity)
                .where(nameContains == null || nameContains.isBlank() ? null
                                : artistEntity.name.lower().like(LikePattern.contains(nameContains), LikePattern.ESCAPE),
                        genre == null ? null : Expressions.booleanTemplate("array_contains({0}, {1})", artistEntity.genres, genre))
                .orderBy(artistEntity.name.asc(), artistEntity.id.asc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    @Override
    public List<SourceHash> findHashes(String source, Collection<String> externalIds) {
        return query.select(Projections.constructor(SourceHash.class, artistEntity.externalId, artistEntity.sourceHash))
                .from(artistEntity)
                .where(artistEntity.source.eq(source), artistEntity.externalId.in(externalIds))
                .fetch();
    }

    @Override
    public ArtistEntity save(ArtistEntity artist) {
        return jpa.save(artist);
    }
}
