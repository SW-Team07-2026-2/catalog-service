package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.VinylColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 옵션 수집 요청. 상품 안에서 이름으로 식별하고 LP는 색상, 굿즈는 크기가 필수입니다.
 * 수량은 절대값이며 null이면 유지합니다. 가격이 null이면 기존 값을 유지하고 신규 옵션은 거절합니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OptionUpsert(@NotBlank @Size(max = 100) String name,
                           VinylColor vinylColor,
                           OptionSize size,
                           @PositiveOrZero Integer price,
                           @PositiveOrZero Integer qty) {}
