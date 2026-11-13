package com.indie.catalog.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 재고 조정 — delta 는 입고 +, 회수 · 파손 −. reason 은 로그에 남깁니다 (누가 했는지는 Studio 의 작업 기록) */
public record AdminStockAdjust(@NotNull Integer delta, @NotBlank @Size(max = 200) String reason) {}
