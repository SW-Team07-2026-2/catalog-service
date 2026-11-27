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
import static com.indie.catalog.domain.product.entity.QStockEntity.stockEntity;

/** 목록 필터 공통 (팔로우 탭) - 아티스트 ID 목록, 가격대 */
public final class ListFilters {

    /** 팔로우한 아티스트를 한 번에 넘길 수 있는 최대 수 (쿼리 문자열 길이) */
    public static final int MAX_ARTIST_IDS = 200;

    /** 가격 슬라이더 끝 (프론트 요구 2-3) - maxPrice 가 이 값 이상이면 '15만원 이상' = 상한 없음 */
    public static final int PRICE_OPEN_END = 150_000;

    private ListFilters() {}

    /**
     * 아티스트 ID의 중복과 개수를 검증하고 입력 순서를 유지한 목록을 반환합니다.
     *
     * @param artistId 아티스트 ID
     * @param artistIds 대상 아티스트 ID 목록
     * @param sort 목록 정렬 기준
     */
    public static List<Long> artistIds(Long artistId, List<Long> artistIds, ListSort sort) {
        if (artistId != null && artistIds != null) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "artistId 와 artistIds 는 하나만 보냅니다");
        }
        return artistIds(artistId == null ? artistIds : List.of(artistId), sort);
    }

    /**
     * 아티스트 ID의 중복과 개수를 검증하고 입력 순서를 유지한 목록을 반환합니다.
     *
     * @param ids 아티스트 ID 목록
     * @param sort 목록 정렬 기준
     */
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

    /**
     * 최소 가격이 최대 가격보다 큰 요청을 거절합니다.
     *
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     */
    public static void checkPrice(Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "minPrice 는 maxPrice 보다 클 수 없습니다");
        }
    }

    /**
     * 넘긴 순서의 자리 (0부터) - ARTIST_ORDER 정렬 키
     *
     * @param artistId 정렬할 아티스트 ID 표현식
     * @param ids 아티스트 ID 목록
     */
    public static NumberExpression<Integer> rank(NumberExpression<Long> artistId, List<Long> ids) {
        CaseBuilder.Cases<Integer, NumberExpression<Integer>> cases = new CaseBuilder().when(artistId.eq(ids.get(0))).then(0);
        for (int i = 1; i < ids.size(); i++) {
            cases = cases.when(artistId.eq(ids.get(i))).then(i);
        }
        return cases.otherwise(ids.size());
    }

    /**
     * 가격대 - 목록에 보이는 최저가(옵션 중 가장 싼 값)가 [minPrice, maxPrice] 안. 둘 다 없으면 조건 없음, maxPrice >= 150,000 은 상한 없음
     *
     * @param productId 조회 대상 상품 ID 표현식
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     */
    public static BooleanExpression lowestPriceBetween(NumberExpression<Long> productId, Integer minPrice, Integer maxPrice) {
        if (maxPrice != null && maxPrice >= PRICE_OPEN_END) maxPrice = null;
        if (minPrice == null && maxPrice == null) return null;
        NumberExpression<Integer> lowest = Expressions.asNumber(JPAExpressions.select(productOptionEntity.price.min())
                .from(productOptionEntity).where(productOptionEntity.productId.eq(productId)));
        if (minPrice == null) return lowest.loe(maxPrice);
        if (maxPrice == null) return lowest.goe(minPrice);
        return lowest.between(minPrice, maxPrice);
    }

    /**
     * 품절 안 보기 - 재고가 남은 옵션이 하나라도 있는 상품만 (목록 soldOut 이 false 인 것과 같은 기준). false 면 조건 없음
     *
     * @param productId 조회 대상 상품 ID 표현식
     * @param excludeSoldOut 품절 상품 제외 여부
     */
    public static BooleanExpression inStock(NumberExpression<Long> productId, boolean excludeSoldOut) {
        if (!excludeSoldOut) return null;
        return JPAExpressions.selectOne().from(productOptionEntity, stockEntity)
                .where(productOptionEntity.productId.eq(productId), stockEntity.optionId.eq(productOptionEntity.id), stockEntity.qty.gt(0))
                .exists();
    }
}
