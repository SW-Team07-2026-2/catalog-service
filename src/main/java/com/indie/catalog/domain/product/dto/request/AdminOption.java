package com.indie.catalog.domain.product.dto.request;

import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.VinylColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 관리자가 넣는 옵션 — id 가 있으면 그 옵션을 고치고(이름도 바꿀 수 있음), 없으면 이름으로 찾아 고치거나 새로 만듭니다.
 * vinylColor · size 규칙은 수집과 같습니다 (OptionRule). qty 는 등록 때만 받습니다 — 그 뒤 수량은 재고 조정으로 바꿉니다.
 */
public record AdminOption(@Positive Long id,
                          @NotBlank @Size(max = 100) String name,
                          VinylColor vinylColor,
                          OptionSize size,
                          @NotNull @PositiveOrZero Integer price,
                          @PositiveOrZero Integer qty) {}
