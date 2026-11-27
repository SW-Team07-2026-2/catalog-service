package com.indie.catalog.domain.product.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** product_option.size <-> OptionSize - DB 에는 value("S", "FREE", "0" ...)를 씁니다 (V2 의 ck_product_option_size) */
@Converter
public class OptionSizeConverter implements AttributeConverter<OptionSize, String> {

    @Override
    public String convertToDatabaseColumn(OptionSize size) {
        return size == null ? null : size.value();
    }

    @Override
    public OptionSize convertToEntityAttribute(String value) {
        return value == null ? null : OptionSize.of(value);
    }
}
