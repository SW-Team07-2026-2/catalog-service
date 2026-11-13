package com.indie.catalog.domain.search.repository;

import com.indie.catalog.domain.search.dto.response.SearchHit;
import com.indie.catalog.global.common.util.LikePattern;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.indie.catalog.domain.album.entity.QAlbumEntity.albumEntity;
import static com.indie.catalog.domain.album.entity.QTrackEntity.trackEntity;
import static com.indie.catalog.domain.artist.entity.QArtistEntity.artistEntity;

/**
 * pg_trgm — lower(이름) like 로 거르고 similarity() 로 정렬합니다. 대상마다 한 번씩 조회해 점수순으로 합칩니다.
 * 3글자 이상은 lower(이름) 트라이그램 GIN 인덱스를 타고, 1~2글자는 순차 스캔이지만 폴백 경로라 허용합니다 (08 §3).
 * pg_trgm 에는 불용어가 없어 "The" 같은 이름도 그대로 찾습니다.
 */
@Repository
public class SearchRepositoryAdapter implements SearchRepository {

    private static final Comparator<SearchHit> BY_SCORE = Comparator.comparingDouble(SearchHit::score).reversed()
            .thenComparing(SearchHit::type).thenComparingLong(SearchHit::id);

    private final JPAQueryFactory query;

    public SearchRepositoryAdapter(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public List<SearchHit> search(String q, int limit) {
        String pattern = LikePattern.contains(q);
        List<SearchHit> hits = new ArrayList<>();

        NumberExpression<Double> artistScore = similarity(artistEntity.name, q);
        for (Tuple t : query.select(artistEntity.id, artistEntity.name, artistScore)
                .from(artistEntity)
                .where(artistEntity.name.lower().like(pattern, LikePattern.ESCAPE))
                .orderBy(artistScore.desc(), artistEntity.id.asc())
                .limit(limit)
                .fetch()) {
            hits.add(new SearchHit("ARTIST", t.get(artistEntity.id), t.get(artistEntity.name), null, t.get(artistScore)));
        }

        NumberExpression<Double> albumScore = similarity(albumEntity.title, q);
        for (Tuple t : query.select(albumEntity.id, albumEntity.title, albumScore)
                .from(albumEntity)
                .where(albumEntity.title.lower().like(pattern, LikePattern.ESCAPE))
                .orderBy(albumScore.desc(), albumEntity.id.asc())
                .limit(limit)
                .fetch()) {
            hits.add(new SearchHit("ALBUM", t.get(albumEntity.id), t.get(albumEntity.title), null, t.get(albumScore)));
        }

        NumberExpression<Double> trackScore = similarity(trackEntity.title, q);
        for (Tuple t : query.select(trackEntity.id, trackEntity.title, trackEntity.albumId, trackScore)
                .from(trackEntity)
                .where(trackEntity.title.lower().like(pattern, LikePattern.ESCAPE))
                .orderBy(trackScore.desc(), trackEntity.id.asc())
                .limit(limit)
                .fetch()) {
            hits.add(new SearchHit("TRACK", t.get(trackEntity.id), t.get(trackEntity.title), t.get(trackEntity.albumId),
                    t.get(trackScore)));
        }

        return hits.stream().sorted(BY_SCORE).limit(limit).toList();
    }

    /** similarity 는 public 스키마의 pg_trgm 함수 — search_path 가 catalog 라 스키마를 붙입니다 */
    private static NumberExpression<Double> similarity(StringPath column, String q) {
        return Expressions.numberTemplate(Double.class, "cast(function('public.similarity', {0}, {1}) as Double)", column, q);
    }
}
