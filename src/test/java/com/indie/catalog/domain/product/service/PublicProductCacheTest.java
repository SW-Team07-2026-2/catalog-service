package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.ProductDetail;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ✏️ 11/25 공개 목록 · 상세 Pod 캐시 — 같은 요청은 3초 안에 한 번만 DB 로, 다른 조건은 따로, 없는 상품은 남기지 않음 */
class PublicProductCacheTest {

    private final ProductService service = mock(ProductService.class);
    private final PublicProductCache cache = new PublicProductCache(service);

    @Test
    void sameProductIsLoadedOnce() {
        ProductDetail detail = new ProductDetail(7L, null, "굿즈", null, 1L, "아티스트", null, null, null, null, null, null, List.of());
        when(service.product(7L)).thenReturn(detail);

        assertThat(cache.product(7L)).isSameAs(detail);
        assertThat(cache.product(7L)).isSameAs(detail);

        verify(service, times(1)).product(7L);
    }

    @Test
    void missingProductIsNotCached() {
        when(service.product(9L)).thenThrow(new GeneralException(CommonErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> cache.product(9L)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> cache.product(9L)).isInstanceOf(GeneralException.class);

        verify(service, times(2)).product(9L);
    }

    @Test
    void searchIsCachedPerCondition() {
        PageResult<GoodsItem> page = new PageResult<>(List.of(), 0, 20, false);
        when(service.goods(isNull(), isNull(), any(), any(), any(), any(), anyBoolean(), isNull(), anyInt(), anyInt())).thenReturn(page);

        cache.searchGoods(null, null, null, null, false, 0, 20);
        cache.searchGoods(null, null, null, null, false, 0, 20);              // 같은 조건 — 캐시
        cache.searchGoods(null, null, null, null, false, 1, 20);              // 다음 페이지 — 따로
        cache.searchGoods(List.of(GoodsCategory.values()[0]), null, null, null, false, 0, 20);   // 칩 — 따로
        cache.searchGoods(null, "혁오", null, null, false, 0, 20);            // 검색어 — 따로

        verify(service, times(1)).goods(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
        verify(service, times(1)).goods(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(1), eq(20));
        verify(service, times(1)).goods(isNull(), isNull(), eq(List.of(GoodsCategory.values()[0])), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
        verify(service, times(1)).goods(isNull(), isNull(), isNull(), anyString(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
    }
}
