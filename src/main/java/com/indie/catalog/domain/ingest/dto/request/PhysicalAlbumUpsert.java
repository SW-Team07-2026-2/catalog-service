package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 피지컬 앨범 (LP · CD · 카세트) — 한 앨범에 같은 매체를 여러 개 넣을 수 있습니다 (초판 · 재판 · N차는 name 으로 구분).
 * thumbnail_url 이 없으면 저장된 이미지를, 그것도 없으면 앨범 이미지를 씁니다.
 * 11/26 description(판 상세 설명)은 저장된 값이 없을 때만 채웁니다 — 관리자가 쓴 값은 수집으로 바뀌지 않음. 11/27 상세 이미지는 받지 않습니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PhysicalAlbumUpsert(@NotBlank @Size(max = 128) String externalId,
                                  @Size(max = 128) String sourceHash,
                                  @NotNull PhysicalFormat format,
                                  @NotBlank @Size(max = 255) String name,
                                  LocalDate releasedAt,
                                  @Size(max = 512) String thumbnailUrl,
                                  @NotEmpty List<@Valid @NotNull OptionUpsert> options,
                                  @Size(max = 1000) String description) {}
