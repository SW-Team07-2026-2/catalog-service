package com.indie.catalog.global.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/** 저장은 Spring Data, 릴레이 · 정리 쿼리는 OutboxQueries (QueryDSL) */
public interface OutboxRepository extends JpaRepository<OutboxEntity, Long>, OutboxQueries {
}
