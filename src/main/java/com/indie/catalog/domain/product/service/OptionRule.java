package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;

/**
 * 옵션 값 규칙 — 상품과 옵션이 다른 테이블이라 DB CHECK 로 걸 수 없어 여기서 막습니다 (V1__catalog_schema.sql 주석).
 * <pre>
 * LP            vinyl_color 필수 (BLACK · COLOR) · size 없음
 * CD · 카세트    둘 다 없음 (옵션 이름만 — "기본" 등)
 * 굿즈           size 필수 (사이즈가 없는 품목은 FREE) · vinyl_color 없음
 * </pre>
 * 옵션이 들어오는 길은 ProductService.upsertOptions 하나입니다 (수집 적재 · 관리자 등록이 모두 거칩니다).
 */
public final class OptionRule {

    private OptionRule() {}

    /** format 이 null 이면 굿즈 */
    public static void check(PhysicalFormat format, String optionName, VinylColor vinylColor, OptionSize size) {
        if (format == null) {
            require(vinylColor == null, optionName, "굿즈 옵션에는 vinyl_color 를 쓸 수 없습니다");
            require(size != null, optionName, "굿즈 옵션에는 size 가 필요합니다 (사이즈가 없으면 FREE)");
        } else if (format == PhysicalFormat.LP) {
            require(size == null, optionName, "LP 옵션에는 size 를 쓸 수 없습니다");
            require(vinylColor != null, optionName, "LP 옵션에는 vinyl_color 가 필요합니다 (BLACK · COLOR)");
        } else {
            require(vinylColor == null && size == null, optionName, format + " 옵션에는 vinyl_color · size 를 쓸 수 없습니다");
        }
    }

    private static void require(boolean ok, String optionName, String message) {
        if (!ok) throw new GeneralException(ProductErrorCode.INVALID_OPTION, "옵션 '" + optionName + "': " + message);
    }
}
