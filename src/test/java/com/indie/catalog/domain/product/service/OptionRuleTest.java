package com.indie.catalog.domain.product.service;

import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.VinylColor;
import com.indie.catalog.domain.product.exception.ProductErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DB CHECK 로 못 거는 규칙 (상품 종류 ↔ 옵션 값) */
class OptionRuleTest {

    @Test
    void lpNeedsVinylColorAndNoSize() {
        assertThatCode(() -> OptionRule.check(PhysicalFormat.LP, "초판", VinylColor.BLACK, null)).doesNotThrowAnyException();
        assertInvalid(PhysicalFormat.LP, null, null);
        assertInvalid(PhysicalFormat.LP, VinylColor.COLOR, OptionSize.FREE);
    }

    @Test
    void cdAndCassetteTakeNeither() {
        assertThatCode(() -> OptionRule.check(PhysicalFormat.CD, "기본", null, null)).doesNotThrowAnyException();
        assertThatCode(() -> OptionRule.check(PhysicalFormat.CASSETTE, "기본", null, null)).doesNotThrowAnyException();
        assertInvalid(PhysicalFormat.CD, VinylColor.BLACK, null);
        assertInvalid(PhysicalFormat.CASSETTE, null, OptionSize.FREE);
    }

    @Test
    void goodsNeedSizeAndNoVinylColor() {
        assertThatCode(() -> OptionRule.check(null, "FREE", null, OptionSize.FREE)).doesNotThrowAnyException();
        assertInvalid(null, null, null);
        assertInvalid(null, VinylColor.BLACK, OptionSize.M);
    }

    private static void assertInvalid(PhysicalFormat format, VinylColor vinylColor, OptionSize size) {
        assertThatThrownBy(() -> OptionRule.check(format, "옵션", vinylColor, size))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_OPTION));
    }
}
