package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** artist_id 는 Catalog id — 수집기는 I4 응답의 id 를 씁니다. tracks 가 있으면 통째로 교체 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AlbumUpsert(@NotBlank @Size(max = 32) String source,
                          @NotBlank @Size(max = 128) String externalId,
                          @Size(max = 128) String sourceHash,
                          @NotNull @Positive Long artistId,
                          @NotBlank @Size(max = 200) String title,
                          @NotNull AlbumEntity.Type type,
                          LocalDate releasedAt,
                          @Size(max = 512) String thumbnailUrl,
                          @Size(max = 100) List<@Valid @NotNull TrackUpsert> tracks,
                          @Valid SaleUpsert cd,
                          List<@Valid @NotNull LpUpsert> lps) {}
