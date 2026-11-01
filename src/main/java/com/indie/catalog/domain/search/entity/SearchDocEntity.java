package com.indie.catalog.domain.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/**
 * search_doc — pg_trgm 통합 검색 대상. 쓰기는 SearchIndex 의 upsert 로만 합니다 (이 엔티티는 테이블 정의용).
 * 🔴 GIN(body gin_trgm_ops) 인덱스는 Flyway V1 에만 있습니다 — 로컬(ddl update)은 인덱스 없이 순차 스캔.
 */
@Entity
@Table(name = "search_doc",
        uniqueConstraints = @UniqueConstraint(name = "uq_search_doc_target", columnNames = {"target_type", "target_id"}))
public class SearchDocEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String targetType;

    private Long targetId;

    @Column(columnDefinition = "text")
    private String body;

    private OffsetDateTime updatedAt;

    protected SearchDocEntity() {}
}
