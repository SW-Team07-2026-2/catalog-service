package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.global.common.util.LikePattern;
import com.indie.catalog.global.common.util.ListFilters;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.indie.catalog.domain.album.entity.QAlbumEntity.albumEntity;
import static com.indie.catalog.domain.artist.entity.QArtistEntity.artistEntity;
import static com.indie.catalog.domain.product.entity.QGoodsEntity.goodsEntity;
import static com.indie.catalog.domain.product.entity.QPhysicalAlbumEntity.physicalAlbumEntity;

/** ArtistRepository 구현 — 단건 · 저장은 Spring Data, 목록 필터 · 해시 조회는 QueryDSL */
@Repository
public class ArtistRepositoryAdapter implements ArtistRepository {

    /** 발매일(DATE)을 시각으로 바꿀 때의 하루 경계 */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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
    public List<ArtistEntity> search(String nameContains, List<Genre> genres, List<ArtistGender> genders, List<ArtistComposition> compositions,
                                     List<Long> ids, ListSort sort, long offset, int limit) {
        return query.selectFrom(artistEntity)
                .where(nameContains == null || nameContains.isBlank() ? null
                                : artistEntity.name.lower().like(LikePattern.contains(nameContains), LikePattern.ESCAPE),
                        genres == null || genres.isEmpty() ? null : ExpressionUtils.anyOf(genres.stream()
                                .map(g -> (Predicate) Expressions.booleanTemplate("array_contains({0}, {1})", artistEntity.genres, g))
                                .toList()),
                        genders == null || genders.isEmpty() ? null : artistEntity.gender.in(genders),
                        compositions == null || compositions.isEmpty() ? null : artistEntity.composition.in(compositions),
                        ids == null || ids.isEmpty() ? null : artistEntity.id.in(ids))
                .orderBy(order(sort, ids))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    private static OrderSpecifier<?>[] order(ListSort sort, List<Long> ids) {
        if (sort == ListSort.ARTIST_ORDER) return new OrderSpecifier<?>[]{ListFilters.rank(artistEntity.id, ids).asc()};
        if (sort == ListSort.UPDATED) return new OrderSpecifier<?>[]{artistEntity.updatedAt.desc().nullsLast(), artistEntity.id.desc()};
        // 이름순은 유니코드 순(ucs_basic = 가나다순) — DB 기본 정렬(en_US.utf8 · glibc 2.28+)은 한글 순서가 깨집니다
        return new OrderSpecifier<?>[]{Expressions.stringTemplate("collate({0} as ucs_basic)", artistEntity.name).asc(), artistEntity.id.asc()};
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

    @Override
    public Map<Long, OffsetDateTime> findLatestContentAt(Collection<Long> artistIds) {
        Map<Long, OffsetDateTime> latest = new HashMap<>();
        if (artistIds.isEmpty()) return latest;
        for (Tuple t : query.select(albumEntity.artistId, albumEntity.releasedAt.max()).from(albumEntity)
                .where(albumEntity.artistId.in(artistIds)).groupBy(albumEntity.artistId).fetch()) {
            keepLater(latest, t.get(albumEntity.artistId), startOfDay(t.get(albumEntity.releasedAt.max())));
        }
        for (Tuple t : query.select(albumEntity.artistId, physicalAlbumEntity.releasedAt.max()).from(physicalAlbumEntity)
                .join(albumEntity).on(albumEntity.id.eq(physicalAlbumEntity.albumId))
                .where(albumEntity.artistId.in(artistIds)).groupBy(albumEntity.artistId).fetch()) {
            keepLater(latest, t.get(albumEntity.artistId), startOfDay(t.get(physicalAlbumEntity.releasedAt.max())));
        }
        for (Tuple t : query.select(goodsEntity.artistId, goodsEntity.createdAt.max()).from(goodsEntity)
                .where(goodsEntity.artistId.in(artistIds)).groupBy(goodsEntity.artistId).fetch()) {
            keepLater(latest, t.get(goodsEntity.artistId), t.get(goodsEntity.createdAt.max()));
        }
        return latest;
    }

    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(SEOUL).toOffsetDateTime();
    }

    private static void keepLater(Map<Long, OffsetDateTime> latest, Long artistId, OffsetDateTime at) {
        if (at != null) latest.merge(artistId, at, (x, y) -> x.isAfter(y) ? x : y);
    }

    @Override
    @Transactional
    public int updateFollowerCounts(Map<Long, Long> counts) {
        int changed = 0;
        for (Map.Entry<Long, Long> e : counts.entrySet()) {
            changed += jpa.updateFollowerCount(e.getKey(), e.getValue());
        }
        return changed;
    }

    @Override
    public List<Long> findIdsAfter(long afterId, int limit) {
        return query.select(artistEntity.id).from(artistEntity)
                .where(artistEntity.id.gt(afterId))
                .orderBy(artistEntity.id.asc())
                .limit(limit)
                .fetch();
    }
}
