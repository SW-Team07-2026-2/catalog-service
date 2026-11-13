package com.indie.catalog.domain.cart.repository;

import com.indie.catalog.domain.cart.entity.CartItemEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import static com.indie.catalog.domain.cart.entity.QCartItemEntity.cartItemEntity;

/** CartItemRepository 구현 — 담기는 HQL(insert … on conflict), 조회 · 삭제는 QueryDSL */
@Repository
public class CartItemRepositoryAdapter implements CartItemRepository {

    private final JPAQueryFactory query;
    private final EntityManager em;

    public CartItemRepositoryAdapter(JPAQueryFactory query, EntityManager em) {
        this.query = query;
        this.em = em;
    }

    @Override
    public List<CartItemEntity> findByUserId(long userId) {
        return query.selectFrom(cartItemEntity)
                .where(cartItemEntity.userId.eq(userId))
                .orderBy(cartItemEntity.createdAt.desc(), cartItemEntity.optionId.desc())
                .fetch();
    }

    /** 같은 옵션을 동시에 두 번 담아도 한 줄 — PK(user_id, option_id) 충돌이면 수량만 덮어씁니다 */
    @Override
    public void put(long userId, long optionId, int qty, OffsetDateTime now) {
        em.createQuery("""
                        insert into CartItemEntity (userId, optionId, qty, createdAt)
                        values (:userId, :optionId, :qty, :now)
                        on conflict (userId, optionId) do update set qty = excluded.qty
                        """)
                .setParameter("userId", userId)
                .setParameter("optionId", optionId)
                .setParameter("qty", qty)
                .setParameter("now", now)
                .executeUpdate();
        // 벌크 쿼리는 영속성 컨텍스트를 건너뜁니다 — 이 트랜잭션에서 이미 읽은 행이 있으면 옛 수량을 들고 있으므로 떼어냅니다
        em.detach(em.getReference(CartItemEntity.class, new CartItemEntity.Key(userId, optionId)));
    }

    @Override
    public boolean delete(long userId, long optionId) {
        return query.delete(cartItemEntity)
                .where(cartItemEntity.userId.eq(userId), cartItemEntity.optionId.eq(optionId))
                .execute() == 1;
    }

    @Override
    public long deleteOptions(long userId, Collection<Long> optionIds) {
        return query.delete(cartItemEntity)
                .where(cartItemEntity.userId.eq(userId), cartItemEntity.optionId.in(optionIds))
                .execute();
    }

    @Override
    public long deleteByUserId(long userId) {
        return query.delete(cartItemEntity)
                .where(cartItemEntity.userId.eq(userId))
                .execute();
    }
}
