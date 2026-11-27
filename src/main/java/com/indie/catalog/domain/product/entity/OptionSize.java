package com.indie.catalog.domain.product.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 굿즈 옵션의 사이즈 - 사이즈가 없는 품목은 FREE. 숫자 사이즈(0 ~ 4)는 브랜드 표기 그대로 씁니다 (예: 혁오 로고 티셔츠).
 * API, DB 에는 value 가 들어갑니다 - 숫자 사이즈는 Java 이름이 SIZE_0 이지만 값은 "0" (OptionSizeConverter).
 */
public enum OptionSize {
    XS("XS"), S("S"), M("M"), L("L"), XL("XL"), XXL("XXL"), FREE("FREE"),
    SIZE_0("0"), SIZE_1("1"), SIZE_2("2"), SIZE_3("3"), SIZE_4("4"), SIZE_5("5"), SIZE_6("6");

    private final String value;

    OptionSize(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static OptionSize of(String value) {
        return Arrays.stream(values()).filter(s -> s.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 사이즈: " + value));
    }
}
