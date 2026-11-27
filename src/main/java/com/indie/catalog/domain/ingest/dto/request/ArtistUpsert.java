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
 * 아티스트 수집 요청. source_hash가 같으면 항목 전체를 건너뜁니다.
 * 장르가 null이면 빈 배열로 처리하며 굿즈, 이미지, 성별, 구성은 null이면 유지합니다.
 * 소개 글은 저장된 글이 없을 때만 채웁니다.
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
