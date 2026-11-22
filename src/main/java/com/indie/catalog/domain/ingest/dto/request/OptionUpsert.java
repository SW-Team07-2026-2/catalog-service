package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.VinylColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 판매 단위 — 한 상품 안에서 name 이 식별자입니다 (같은 이름이면 갱신 · 새 이름이면 새 옵션).
 * vinyl_color 는 LP 옵션에 필수 · size 는 굿즈 옵션에 필수(사이즈가 없으면 FREE) · CD 와 카세트는 둘 다 비웁니다 (OptionRule).
 * qty 는 입고 수량(절대값) · null 이면 수량은 그대로 둡니다 (새 옵션이면 0).
 * price 가 null 이면 저장된 판매가를 그대로 둡니다 — 새 옵션은 판매가가 있어야 합니다 (11/18: 판매가는 관리자가 넣는 값).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OptionUpsert(@NotBlank @Size(max = 100) String name,
                           VinylColor vinylColor,
                           OptionSize size,
                           @PositiveOrZero Integer price,
                           @PositiveOrZero Integer qty) {}
