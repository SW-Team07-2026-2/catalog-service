package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.StockEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.product.entity.QStockEntity.stockEntity;

/** StockRepository 구현 — 차감 · 복구는 QueryDSL 조건부 UPDATE (엔티티를 읽지 않고 DB 에서 바로 계산) */
@Repository
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository jpa;
    private final JPAQueryFactory query;
    private final Clock clock;

    public StockRepositoryAdapter(StockJpaRepository jpa, JPAQueryFactory query, Clock clock) {
        this.jpa = jpa;
        this.query = query;
        this.clock = clock;
    }

    @Override
    public List<StockEntity> findAllById(Iterable<Long> optionIds) {
        return jpa.findAllById(optionIds);
    }

    @Override
    public StockEntity save(StockEntity stock) {
        return jpa.save(stock);
    }

    @Override
    public int decrement(long optionId, int qty) {
        return (int) query.update(stockEntity)
                .set(stockEntity.qty, stockEntity.qty.subtract(qty))
                .set(stockEntity.updatedAt, OffsetDateTime.now(clock))
                .where(stockEntity.optionId.eq(optionId), stockEntity.qty.goe(qty))
                .execute();
    }

    @Override
    public int restore(long optionId, int qty) {
        return (int) query.update(stockEntity)
                .set(stockEntity.qty, stockEntity.qty.add(qty))
                .set(stockEntity.updatedAt, OffsetDateTime.now(clock))
                .where(stockEntity.optionId.eq(optionId))
                .execute();
    }

    @Override
    public Optional<StockEntity> findForUpdate(Long optionId) {
        return Optional.ofNullable(query.selectFrom(stockEntity)
                .where(stockEntity.optionId.eq(optionId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne());
    }
}
