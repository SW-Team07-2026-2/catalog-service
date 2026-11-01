package com.indie.catalog.domain.search.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * pg_trgm: ILIKE 로 거르고 similarity() 로 정렬합니다. 3글자 이상은 GIN 인덱스를 타고, 1~2글자는 순차 스캔이지만
 * 폴백 경로라 허용합니다 (08 §3). pg_trgm 에는 불용어가 없어 "The" 같은 이름도 그대로 찾습니다.
 */
@Repository
public class JdbcSearchIndex implements SearchIndex {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSearchIndex(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void put(TargetType type, long targetId, String body) {
        jdbc.update("""
                INSERT INTO search_doc (target_type, target_id, body, updated_at)
                VALUES (:type, :targetId, :body, now())
                ON CONFLICT (target_type, target_id) DO UPDATE SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("targetId", targetId)
                .addValue("body", body));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeTracksOf(long albumId) {
        jdbc.update("""
                DELETE FROM search_doc
                WHERE target_type = 'TRACK' AND target_id IN (SELECT id FROM track WHERE album_id = :albumId)
                """, new MapSqlParameterSource("albumId", albumId));
    }

    @Override
    public List<SearchHit> search(String q, int limit) {
        // similarity 는 public 스키마의 확장 함수 — search_path 가 catalog 라 한정합니다
        return jdbc.query("""
                SELECT target_type, target_id, body, public.similarity(body, :q) AS score
                FROM search_doc
                WHERE body ILIKE :pattern ESCAPE '\\'
                ORDER BY score DESC, target_type, target_id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("q", q)
                .addValue("pattern", "%" + escapeLike(q) + "%")
                .addValue("limit", limit),
                (rs, i) -> new SearchHit(rs.getString("target_type"), rs.getLong("target_id"), rs.getString("body"),
                        rs.getDouble("score")));
    }

    /** 검색어의 % · _ 가 와일드카드로 동작하면 "%" 한 글자로 전체가 나옵니다 */
    static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
