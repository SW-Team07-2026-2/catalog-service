package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsEntity;
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

import static com.indie.catalog.domain.artist.entity.QArtistEntity.artistEntity;
import static com.indie.catalog.domain.product.entity.QGoodsEntity.goodsEntity;

/** GoodsRepository 구현 - 단건, 저장은 Spring Data, 목록, 해시 조회는 QueryDSL */
@Repository
public class GoodsRepositoryAdapter implements GoodsRepository {

    private final GoodsJpaRepository jpa;
    private final JPAQueryFactory query;

    public GoodsRepositoryAdapter(GoodsJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public Optional<GoodsEntity> findBySourceAndExternalId(String source, String externalId) {
        return jpa.findBySourceAndExternalId(source, externalId);
    }

    @Override
    public List<GoodsEntity> search(Filter f, long offset, int limit) {
        return query.selectFrom(goodsEntity)
                .where(f.artistIds() == null || f.artistIds().isEmpty() ? null : goodsEntity.artistId.in(f.artistIds()),
                        f.categories() == null || f.categories().isEmpty() ? null : goodsEntity.category.in(f.categories()),
                        nameOrArtist(f.q()),
                        ListFilters.lowestPriceBetween(goodsEntity.id, f.minPrice(), f.maxPrice()),
                        ListFilters.inStock(goodsEntity.id, f.excludeSoldOut()))
                .orderBy(order(f))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    private static BooleanExpression nameOrArtist(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = LikePattern.contains(q);
        return goodsEntity.name.lower().like(pattern, LikePattern.ESCAPE).or(goodsEntity.artistId.in(
                JPAExpressions.select(artistEntity.id).from(artistEntity).where(artistEntity.name.lower().like(pattern, LikePattern.ESCAPE))));
    }

    private static OrderSpecifier<?>[] order(Filter f) {
        if (f.sort() == ListSort.ARTIST_ORDER) {
            return new OrderSpecifier<?>[]{ListFilters.rank(goodsEntity.artistId, f.artistIds()).asc(), goodsEntity.id.desc()};
        }
        if (f.sort() == ListSort.UPDATED) return new OrderSpecifier<?>[]{goodsEntity.updatedAt.desc(), goodsEntity.id.desc()};
        return new OrderSpecifier<?>[]{goodsEntity.id.desc()};
    }

    @Override
    public List<GoodsEntity> findByArtistId(Long artistId) {
        return query.selectFrom(goodsEntity)
                .where(goodsEntity.artistId.eq(artistId))
                .orderBy(goodsEntity.id.desc())
                .fetch();
    }

    @Override
    public List<SourceHash> findHashes(String source, Collection<String> externalIds) {
        return query.select(Projections.constructor(SourceHash.class, goodsEntity.externalId, goodsEntity.sourceHash))
                .from(goodsEntity)
                .where(goodsEntity.source.eq(source), goodsEntity.externalId.in(externalIds))
                .fetch();
    }

    @Override
    public GoodsEntity save(GoodsEntity goods) {
        return jpa.save(goods);
    }
}
