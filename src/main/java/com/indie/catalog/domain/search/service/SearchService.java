package com.indie.catalog.domain.search.service;

import com.indie.catalog.domain.search.dto.response.SearchResult;
import com.indie.catalog.domain.search.exception.SearchErrorCode;
import com.indie.catalog.domain.search.repository.SearchRepository;
import com.indie.catalog.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 키워드 검색 (C1 · FR-CAT-08) — AI 자연어 검색(주경로)의 폴백 최종단. 아티스트 · 앨범 · 트랙 이름을 직접 조회합니다 */
@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 50;

    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Transactional(readOnly = true)
    public SearchResult search(String rawQuery, int size) {
        String q = rawQuery == null ? "" : rawQuery.strip();
        if (q.isEmpty()) throw new GeneralException(SearchErrorCode.SEARCH_QUERY_EMPTY);
        if (q.length() > MAX_QUERY_LENGTH) throw new GeneralException(SearchErrorCode.SEARCH_QUERY_TOO_LONG);
        return new SearchResult(searchRepository.search(q, size));
    }
}
