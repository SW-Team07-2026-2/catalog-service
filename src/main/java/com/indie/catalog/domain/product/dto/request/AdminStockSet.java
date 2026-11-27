package com.indie.catalog.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 재고 설정 - 옵션의 재고를 qty 개로 맞춥니다 (조정은 AdminStockAdjust). reason 은 로그에 남깁니다 */
public record AdminStockSet(@NotNull @PositiveOrZero Integer qty, @NotBlank @Size(max = 200) String reason) {}
