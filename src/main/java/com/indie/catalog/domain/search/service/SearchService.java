package com.indie.catalog.domain.search.service;

import com.indie.catalog.domain.album.entity.TrackEntity;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.search.dto.response.SearchHit;
import com.indie.catalog.domain.search.dto.response.SearchResult;
import com.indie.catalog.domain.search.repository.SearchIndex;
import com.indie.catalog.domain.search.exception.SearchErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 키워드 검색 (C1 · FR-CAT-08) — AI 자연어 검색(주경로)의 폴백 최종단 */
@Service
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 50;

    private final SearchIndex searchIndex;
    private final TrackRepository trackRepository;

    public SearchService(SearchIndex searchIndex, TrackRepository trackRepository) {
        this.searchIndex = searchIndex;
        this.trackRepository = trackRepository;
    }

    @Transactional(readOnly = true)
    public SearchResult search(String rawQuery, int size) {
        String q = rawQuery == null ? "" : rawQuery.strip();
        if (q.isEmpty()) throw new GeneralException(SearchErrorCode.SEARCH_QUERY_EMPTY);
        if (q.length() > MAX_QUERY_LENGTH) throw new GeneralException(SearchErrorCode.SEARCH_QUERY_TOO_LONG);
        return new SearchResult(withAlbumIds(searchIndex.search(q, size)));
    }

    /** 트랙은 앨범 화면으로 이동해야 하므로 albumId 를 붙입니다 */
    private List<SearchHit> withAlbumIds(List<SearchIndex.SearchHit> hits) {
        List<Long> trackIds = hits.stream().filter(h -> "TRACK".equals(h.type())).map(SearchIndex.SearchHit::id).toList();
        Map<Long, Long> albumOf = trackIds.isEmpty() ? Map.of() : trackRepository.findAllById(trackIds).stream()
                .collect(Collectors.toMap(TrackEntity::getId, TrackEntity::getAlbumId));
        return hits.stream().map(h -> new SearchHit(h.type(), h.id(), h.body(),
                "TRACK".equals(h.type()) ? albumOf.get(h.id()) : null, h.score())).toList();
    }
}
