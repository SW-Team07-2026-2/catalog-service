package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.global.common.util.SourceHash;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.product.entity.QGoodsEntity.goodsEntity;

/** GoodsRepository 구현 — 단건 · 저장은 Spring Data, 목록 · 해시 조회는 QueryDSL */
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
    public List<GoodsEntity> findLatest(Long artistId, GoodsCategory category, long offset, int limit) {
        return query.selectFrom(goodsEntity)
                .where(artistId == null ? null : goodsEntity.artistId.eq(artistId),
                        category == null ? null : goodsEntity.category.eq(category))
                .orderBy(goodsEntity.id.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
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
