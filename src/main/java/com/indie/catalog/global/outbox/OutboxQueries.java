package com.indie.catalog.global.outbox;

import java.time.OffsetDateTime;
import java.util.List;

/** OutboxRepository 의 QueryDSL 부분 — 구현은 OutboxQueriesImpl */
public interface OutboxQueries {

    /** ★ replica 2 가 동시에 릴레이해도 같은 행을 집지 않도록 SKIP LOCKED */
    List<OutboxEntity> lockUnpublished(int limit);

    int deletePublishedBefore(OffsetDateTime cutoff);
}
