package com.indie.catalog.domain.search.repository;

import com.indie.catalog.domain.search.entity.SearchDocEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SearchDocJpaRepository extends JpaRepository<SearchDocEntity, Long> {

    /** (target_type, target_id) 가 있으면 본문만 갈아끼웁니다 */
    @Modifying
    @Query(value = """
            INSERT INTO search_doc (target_type, target_id, body, updated_at)
            VALUES (:type, :targetId, :body, now())
            ON CONFLICT (target_type, target_id) DO UPDATE SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(@Param("type") String type, @Param("targetId") long targetId, @Param("body") String body);

    @Modifying
    @Query("""
            DELETE FROM SearchDocEntity d
            WHERE d.targetType = 'TRACK' AND d.targetId IN (SELECT t.id FROM TrackEntity t WHERE t.albumId = :albumId)
            """)
    int deleteTracksOf(@Param("albumId") long albumId);

    /** similarity 는 public 스키마의 확장 함수 — search_path 가 catalog 라 한정합니다 */
    @Query(value = """
            SELECT target_type AS type, target_id AS id, body, CAST(public.similarity(body, :q) AS float8) AS score
            FROM search_doc
            WHERE body ILIKE :pattern ESCAPE '\\'
            ORDER BY score DESC, target_type, target_id
            LIMIT :limit
            """, nativeQuery = true)
    List<SearchHitRow> search(@Param("q") String q, @Param("pattern") String pattern, @Param("limit") int limit);

    interface SearchHitRow {
        String getType();
        Long getId();
        String getBody();
        Double getScore();
    }
}
