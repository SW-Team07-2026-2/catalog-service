package com.indie.catalog.domain.search.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * pg_trgm: ILIKE 로 거르고 similarity() 로 정렬합니다. 3글자 이상은 GIN 인덱스를 타고, 1~2글자는 순차 스캔이지만
 * 폴백 경로라 허용합니다 (08 §3). pg_trgm 에는 불용어가 없어 "The" 같은 이름도 그대로 찾습니다.
 */
@Repository
public class SearchIndexAdapter implements SearchIndex {

    private final SearchDocJpaRepository jpa;

    public SearchIndexAdapter(SearchDocJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void put(TargetType type, long targetId, String body) {
        jpa.upsert(type.name(), targetId, body);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeTracksOf(long albumId) {
        jpa.deleteTracksOf(albumId);
    }

    @Override
    public List<SearchHit> search(String q, int limit) {
        return jpa.search(q, "%" + escapeLike(q) + "%", limit).stream()
                .map(r -> new SearchHit(r.getType(), r.getId(), r.getBody(), r.getScore()))
                .toList();
    }

    /** 검색어의 % · _ 가 와일드카드로 동작하면 "%" 한 글자로 전체가 나옵니다 */
    static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
