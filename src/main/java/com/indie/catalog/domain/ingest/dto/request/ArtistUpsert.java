package com.indie.catalog.domain.ingest.dto.request;

import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.artist.entity.Genre;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * source_hash 가 저장된 값과 같으면 그 항목은 통째로 건너뜁니다 (UNCHANGED).
 * genres 는 정해진 값만 (Genre) — null 은 빈 배열로 봅니다. goods 가 null 이면 굿즈는 건드리지 않습니다.
 * profile_url · 굿즈 thumbnail_url 이 null 이면 저장된 이미지를 그대로 둡니다 (관리자가 넣은 이미지 · 11/18).
 * 11/22 gender · composition 은 null 이면 저장된 값을 그대로 둡니다 (수집기는 보내지 않아도 됨).
 * 11/25 bio(소개 글)는 저장된 글이 없을 때만 채웁니다 — 수집기가 못 찾으면 null, 관리자가 쓴 글은 수집으로 바뀌지 않음.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ArtistUpsert(@NotBlank @Size(max = 32) String source,
                           @NotBlank @Size(max = 128) String externalId,
                           @Size(max = 128) String sourceHash,
                           @NotBlank @Size(max = 100) String name,
                           List<@NotNull Genre> genres,
                           @Size(max = 100) String label,
                           @Min(1900) @Max(2100) Integer debutYear,
                           @Size(max = 512) String profileUrl,
                           ArtistGender gender,
                           ArtistComposition composition,
                           List<@Valid @NotNull GoodsUpsert> goods,
                           @Size(max = 1000) String bio) {}
