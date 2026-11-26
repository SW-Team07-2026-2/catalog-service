package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 굿즈 — 색상은 이름에 넣고(색상마다 상품 1개) 옵션은 사이즈만 둡니다. category 는 정해진 값만 (목록 밖은 ETC).
 * 11/26 description(상세 설명) · detail_image_urls(상세 이미지)는 저장된 값이 없을 때만 채웁니다 — 관리자가 쓴 값은 수집으로 바뀌지 않음.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GoodsUpsert(@NotBlank @Size(max = 128) String externalId,
                          @Size(max = 128) String sourceHash,
                          @NotBlank @Size(max = 255) String name,
                          @NotNull GoodsCategory category,
                          @Size(max = 512) String thumbnailUrl,
                          @NotEmpty List<@Valid @NotNull OptionUpsert> options,
                          @Size(max = 1000) String description,
                          @Size(max = 10) List<@NotBlank @Size(max = 512) String> detailImageUrls) {}
