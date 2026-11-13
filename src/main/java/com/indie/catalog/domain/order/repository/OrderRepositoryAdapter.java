package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.domain.order.entity.PurchaseOrderEntity.Status;
import com.indie.catalog.global.common.util.Cursor;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.indie.catalog.domain.order.entity.QPurchaseOrderEntity.purchaseOrderEntity;

/** OrderRepository 구현 — 단건 · 저장은 Spring Data, 상태 전이 · 목록은 QueryDSL */
@Repository
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpa;
    private final JPAQueryFactory query;

    public OrderRepositoryAdapter(OrderJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public Optional<PurchaseOrderEntity> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<PurchaseOrderEntity> findByIdAndUserId(Long id, Long userId) {
        return jpa.findByIdAndUserId(id, userId);
    }

    @Override
    public List<PurchaseOrderEntity> findPage(long userId, Cursor cursor, int limit) {
        return query.selectFrom(purchaseOrderEntity)
                .where(purchaseOrderEntity.userId.eq(userId),
                        cursor == null ? null : purchaseOrderEntity.id.lt(cursor.id()))
                .orderBy(purchaseOrderEntity.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public PurchaseOrderEntity save(PurchaseOrderEntity order) {
        return jpa.save(order);
    }

    @Override
    public int attachPgOrderId(long id, String pgOrderId) {
        return (int) query.update(purchaseOrderEntity)
                .set(purchaseOrderEntity.pgOrderId, pgOrderId)
                .where(purchaseOrderEntity.id.eq(id))
                .execute();
    }

    @Override
    public int confirm(long id) {
        return transition(id, Status.CONFIRMED, Status.PENDING);
    }

    @Override
    public int cancel(long id) {
        return transition(id, Status.CANCELLED, Status.PENDING, Status.CONFIRMED);
    }

    @Override
    public int refund(long id) {
        return transition(id, Status.REFUNDED, Status.PENDING, Status.CONFIRMED);
    }

    @Override
    public int refundCancelled(long id) {
        return transition(id, Status.REFUNDED, Status.CANCELLED);
    }

    /** idx_order_pending (status = 'PENDING' 부분 인덱스) 를 탑니다 */
    @Override
    public List<Long> findExpiredPending(OffsetDateTime before, int limit) {
        return query.select(purchaseOrderEntity.id)
                .from(purchaseOrderEntity)
                .where(purchaseOrderEntity.status.eq(Status.PENDING), purchaseOrderEntity.createdAt.lt(before))
                .orderBy(purchaseOrderEntity.createdAt.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Long> findPendingIdsByUserId(long userId) {
        return query.select(purchaseOrderEntity.id)
                .from(purchaseOrderEntity)
                .where(purchaseOrderEntity.userId.eq(userId), purchaseOrderEntity.status.eq(Status.PENDING))
                .fetch();
    }

    private int transition(long id, Status to, Status... from) {
        return (int) query.update(purchaseOrderEntity)
                .set(purchaseOrderEntity.status, to)
                .where(purchaseOrderEntity.id.eq(id), purchaseOrderEntity.status.in(from))
                .execute();
    }
}
