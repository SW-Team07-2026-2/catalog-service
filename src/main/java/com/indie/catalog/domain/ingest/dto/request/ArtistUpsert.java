package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * source_hash 가 저장된 값과 같으면 그 항목은 통째로 건너뜁니다 (UNCHANGED).
 * members · goods 가 null 이면 기존 값을 그대로 두고, 빈 배열이면 비웁니다 (members) · 아무것도 안 합니다 (goods).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ArtistUpsert(@NotBlank @Size(max = 32) String source,
                           @NotBlank @Size(max = 128) String externalId,
                           @Size(max = 128) String sourceHash,
                           @NotBlank @Size(max = 100) String name,
                           @Size(max = 50) String genre,
                           @Size(max = 100) String label,
                           @Min(1900) @Max(2100) Integer debutYear,
                           @Size(max = 512) String profileUrl,
                           List<@Valid @NotNull MemberUpsert> members,
                           List<@Valid @NotNull GoodsUpsert> goods) {}
