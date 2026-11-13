package com.indie.catalog.domain.search.repository;

import com.indie.catalog.domain.search.dto.response.SearchHit;

import java.util.List;

/** 통합 검색 포트 — 구현은 SearchRepositoryAdapter (아티스트 · 앨범 · 트랙을 직접 조회) */
public interface SearchRepository {

    /** q 를 이름에 포함하는 아티스트 · 앨범 · 트랙을 유사도 순으로. q 의 % · _ 는 글자 그대로 취급합니다 */
    List<SearchHit> search(String q, int limit);
}
