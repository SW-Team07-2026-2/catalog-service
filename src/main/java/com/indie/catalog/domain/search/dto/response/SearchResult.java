package com.indie.catalog.domain.search.dto.response;

import java.util.List;

/** Gateway 폴백 응답({ items: [], degraded: true })과 같은 모양 */
public record SearchResult(List<SearchHit> items) {}
