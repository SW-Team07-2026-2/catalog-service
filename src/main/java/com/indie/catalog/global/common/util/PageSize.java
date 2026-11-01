package com.indie.catalog.global.common.util;

import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.config.CatalogProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** size 파라미터 — 기본 20 · 최대 50 (08 §0.6) */
@Component
public class PageSize {

    private final CatalogProperties.Page props;

    public PageSize(CatalogProperties props) {
        this.props = props.page();
    }

    public int resolve(Integer requested) {
        if (requested == null || requested <= 0) return props.size();
        return Math.min(requested, props.maxSize());
    }

    /** ?ids= 일괄 조회 상한 — 한 화면 분량 */
    public void checkIds(List<Long> ids) {
        if (ids.size() > props.maxSize()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "ids 는 최대 " + props.maxSize() + "개입니다");
        }
    }
}
