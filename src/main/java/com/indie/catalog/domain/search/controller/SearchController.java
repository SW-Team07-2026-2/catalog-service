package com.indie.catalog.domain.search.controller;

import com.indie.catalog.domain.search.dto.response.SearchResult;
import com.indie.catalog.domain.search.service.SearchService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.util.PageSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 API C1 — 키워드 검색 */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final PageSize pageSize;

    public SearchController(SearchService searchService, PageSize pageSize) {
        this.searchService = searchService;
        this.pageSize = pageSize;
    }

    @GetMapping("/api/catalog/search")
    public ApiResponse<SearchResult> search(@RequestParam String q, @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(searchService.search(q, pageSize.resolve(size)));
    }
}
