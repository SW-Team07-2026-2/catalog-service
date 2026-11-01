package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** I15 — type 은 어느 테이블과 비교할지 (15 의 예시에는 없어 추가: 같은 출처가 아티스트 · 앨범을 함께 줌) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DiffRequest(@NotBlank @Size(max = 32) String source,
                          @NotNull DiffType type,
                          @NotEmpty @Size(max = 1000) List<@Valid @NotNull DiffItem> items) {}
