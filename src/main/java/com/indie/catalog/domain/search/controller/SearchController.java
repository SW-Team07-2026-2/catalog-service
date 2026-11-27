package com.indie.catalog.domain.search.controller;

import com.indie.catalog.domain.search.dto.response.SearchResult;
import com.indie.catalog.domain.search.service.SearchService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.util.PageSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트 -> Gateway -> Catalog SearchService의 키워드 검색 API. */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final PageSize pageSize;

    public SearchController(SearchService searchService, PageSize pageSize) {
        this.searchService = searchService;
        this.pageSize = pageSize;
    }

    /**
     * 아티스트, 앨범과 트랙을 키워드로 검색합니다.
     *
     * @param q 검색어
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/search")
    public ApiResponse<SearchResult> search(@RequestParam String q, @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(searchService.search(q, pageSize.resolve(size)));
    }
}
