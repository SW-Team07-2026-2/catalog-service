package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.global.common.util.LikePattern;
import com.indie.catalog.global.common.util.ListFilters;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.album.entity.QAlbumEntity.albumEntity;
import static com.indie.catalog.domain.artist.entity.QArtistEntity.artistEntity;
import static com.indie.catalog.domain.product.entity.QPhysicalAlbumEntity.physicalAlbumEntity;

/** PhysicalAlbumRepository 구현 - 단건, 저장은 Spring Data, 목록, 해시 조회는 QueryDSL */
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

    /** 아티스트는 앨범에 있어 album 을 붙입니다 (album_id NOT NULL - 빠지는 행 없음) */
    @Override
    public List<PhysicalAlbumEntity> search(Filter f, long offset, int limit) {
        return query.selectFrom(physicalAlbumEntity)
                .join(albumEntity).on(albumEntity.id.eq(physicalAlbumEntity.albumId))
                .where(f.formats() == null || f.formats().isEmpty() ? null : physicalAlbumEntity.format.in(f.formats()),
                        f.from() == null ? null : physicalAlbumEntity.releasedAt.goe(f.from()),
                        f.to() == null ? null : physicalAlbumEntity.releasedAt.loe(f.to()),
                        f.artistIds() == null || f.artistIds().isEmpty() ? null : albumEntity.artistId.in(f.artistIds()),
                        nameAlbumOrArtist(f.q()),
                        ListFilters.lowestPriceBetween(physicalAlbumEntity.id, f.minPrice(), f.maxPrice()),
                        ListFilters.inStock(physicalAlbumEntity.id, f.excludeSoldOut()))
                .orderBy(order(f))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    private static BooleanExpression nameAlbumOrArtist(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = LikePattern.contains(q);
        return physicalAlbumEntity.name.lower().like(pattern, LikePattern.ESCAPE)
                .or(albumEntity.title.lower().like(pattern, LikePattern.ESCAPE))
                .or(albumEntity.artistId.in(JPAExpressions.select(artistEntity.id).from(artistEntity)
                        .where(artistEntity.name.lower().like(pattern, LikePattern.ESCAPE))));
    }

    private static OrderSpecifier<?>[] order(Filter f) {
        OrderSpecifier<?> released = physicalAlbumEntity.releasedAt.desc().nullsLast();
        if (f.sort() == ListSort.ARTIST_ORDER) {
            return new OrderSpecifier<?>[]{ListFilters.rank(albumEntity.artistId, f.artistIds()).asc(), released, physicalAlbumEntity.id.desc()};
        }
        if (f.sort() == ListSort.UPDATED) return new OrderSpecifier<?>[]{physicalAlbumEntity.updatedAt.desc(), physicalAlbumEntity.id.desc()};
        return new OrderSpecifier<?>[]{released, physicalAlbumEntity.id.desc()};
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
