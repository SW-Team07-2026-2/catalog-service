package com.indie.catalog.global.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 페이지 응답 — { items, page, size, hasNext }. 카탈로그 목록은 page (0부터) · size 입니다 (08 §0.6).
 * 피드처럼 계속 쌓이는 목록이 아니라 OFFSET 으로 충분합니다. 내 주문 목록만 커서를 씁니다.
 */
public record PageResult<T>(List<T> items, int page, int size, boolean hasNext) {

    /** fetched 는 size + 1 건까지 조회한 결과 — 넘치면 다음 페이지가 있습니다 (count 쿼리 없음) */
    public static <E, T> PageResult<T> of(List<E> fetched, int page, int size, Function<List<E>, List<T>> mapper) {
        boolean hasNext = fetched.size() > size;
        return new PageResult<>(mapper.apply(hasNext ? fetched.subList(0, size) : fetched), page, size, hasNext);
    }

    /** ?ids= 일괄 조회 — 한 페이지로 끝납니다 */
    public static <T> PageResult<T> all(List<T> items) {
        return new PageResult<>(items, 0, items.size(), false);
    }
}
