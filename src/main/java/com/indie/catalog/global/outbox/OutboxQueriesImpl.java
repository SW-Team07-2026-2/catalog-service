package com.indie.catalog.global.outbox;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.List;

import static com.indie.catalog.global.outbox.QOutboxEntity.outboxEntity;

public class OutboxQueriesImpl implements OutboxQueries {

    /** JPA 잠금 대기 시간 힌트 — -2 는 Hibernate 의 SKIP LOCKED (LockOptions.SKIP_LOCKED) */
    private static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";
    private static final int SKIP_LOCKED = -2;

    private final JPAQueryFactory query;

    public OutboxQueriesImpl(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public List<OutboxEntity> lockUnpublished(int limit) {
        return query.selectFrom(outboxEntity)
                .where(outboxEntity.publishedAt.isNull())
                .orderBy(outboxEntity.id.asc())
                .limit(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(LOCK_TIMEOUT, SKIP_LOCKED)
                .fetch();
    }

    @Override
    public int deletePublishedBefore(OffsetDateTime cutoff) {
        return (int) query.delete(outboxEntity)
                .where(outboxEntity.publishedAt.lt(cutoff))
                .execute();
    }
}
