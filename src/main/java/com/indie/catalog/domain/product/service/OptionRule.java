package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;

/**
 * 상품 매체에 맞는 옵션 값을 검증합니다.
 * LP는 색상 필수, 굿즈는 크기 필수이며 CD와 카세트는 두 값 모두 사용하지 않습니다.
 */
public final class OptionRule {

    private OptionRule() {}

    /**
     * format 이 null 이면 굿즈
     *
     * @param format 피지컬 앨범 매체, 굿즈는 null
     * @param optionName 옵션 이름
     * @param vinylColor LP 색상
     * @param size 굿즈 크기, 크기가 없는 품목은 FREE
     */
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
