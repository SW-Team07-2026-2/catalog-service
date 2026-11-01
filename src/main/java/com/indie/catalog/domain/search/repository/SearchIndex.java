package com.indie.catalog.domain.search.repository;

import java.util.List;

/**
 * 통합 검색 색인 포트 (search_doc) — 구현은 JdbcSearchIndex (pg_trgm).
 * 쓰기는 원본(아티스트 · 앨범 · 트랙)과 같은 트랜잭션에서 합니다.
 */
public interface SearchIndex {

    enum TargetType { ARTIST, ALBUM, TRACK }

    void put(TargetType type, long targetId, String body);

    /** 트랙 목록을 갈아끼울 때 — 사라진 트랙이 검색에 남지 않게 (트랙 행을 지우기 전에 불러야 함) */
    void removeTracksOf(long albumId);

    /** q 를 포함하는 문서를 유사도 순으로. q 의 % · _ 는 글자 그대로 취급합니다 */
    List<SearchHit> search(String q, int limit);

    record SearchHit(String type, long id, String body, double score) {}
}
