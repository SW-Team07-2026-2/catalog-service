package com.indie.catalog.global.common.util;

import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;

import java.util.LinkedHashSet;
import java.util.List;

import static com.indie.catalog.domain.product.entity.QProductOptionEntity.productOptionEntity;

/** 목록 필터 공통 (11/19 팔로우 탭) — 아티스트 ID 목록 · 가격대 */
public final class ListFilters {

    /** 팔로우한 아티스트를 한 번에 넘길 수 있는 최대 수 (쿼리 문자열 길이) */
    public static final int MAX_ARTIST_IDS = 200;

    /** 가격 슬라이더 끝 (프론트 요구 2-3) — maxPrice 가 이 값 이상이면 '15만원 이상' = 상한 없음 */
    public static final int PRICE_OPEN_END = 150_000;

    private ListFilters() {}

    /** 단일 artistId(기존) · 여러 artistIds(새) 중 하나 → 순서를 지킨 목록 (중복은 앞의 것) · 없으면 빈 목록 */
    public static List<Long> artistIds(Long artistId, List<Long> artistIds, ListSort sort) {
        if (artistId != null && artistIds != null) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "artistId 와 artistIds 는 하나만 보냅니다");
        }
        return artistIds(artistId == null ? artistIds : List.of(artistId), sort);
    }

    public static List<Long> artistIds(List<Long> ids, ListSort sort) {
        List<Long> distinct = ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
        if (distinct.size() > MAX_ARTIST_IDS) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "아티스트 ID 는 최대 " + MAX_ARTIST_IDS + "개입니다");
        }
        if (sort == ListSort.ARTIST_ORDER && distinct.isEmpty()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "sort=ARTIST_ORDER 는 아티스트 ID 목록이 필요합니다");
        }
        return distinct;
    }

    public static void checkPrice(Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "minPrice 는 maxPrice 보다 클 수 없습니다");
        }
    }

    /** 넘긴 순서의 자리 (0부터) — ARTIST_ORDER 정렬 키 */
    public static NumberExpression<Integer> rank(NumberExpression<Long> artistId, List<Long> ids) {
        CaseBuilder.Cases<Integer, NumberExpression<Integer>> cases = new CaseBuilder().when(artistId.eq(ids.get(0))).then(0);
        for (int i = 1; i < ids.size(); i++) {
            cases = cases.when(artistId.eq(ids.get(i))).then(i);
        }
        return cases.otherwise(ids.size());
    }

    /** 가격대 — 목록에 보이는 최저가(옵션 중 가장 싼 값)가 [minPrice, maxPrice] 안. 둘 다 없으면 조건 없음 · maxPrice ≥ 150,000 은 상한 없음 */
    public static BooleanExpression lowestPriceBetween(NumberExpression<Long> productId, Integer minPrice, Integer maxPrice) {
        if (maxPrice != null && maxPrice >= PRICE_OPEN_END) maxPrice = null;
        if (minPrice == null && maxPrice == null) return null;
        NumberExpression<Integer> lowest = Expressions.asNumber(JPAExpressions.select(productOptionEntity.price.min())
                .from(productOptionEntity).where(productOptionEntity.productId.eq(productId)));
        if (minPrice == null) return lowest.loe(maxPrice);
        if (maxPrice == null) return lowest.goe(minPrice);
        return lowest.between(minPrice, maxPrice);
    }
}
