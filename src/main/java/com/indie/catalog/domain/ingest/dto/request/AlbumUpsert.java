package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 앨범 수집 요청. artist_id는 Catalog ID이며 트랙은 track_no로 맞춥니다.
 * 트랙, 피지컬 앨범, 이미지가 null이면 기존 값을 유지하고 소개 글은 저장된 글이 없을 때만 채웁니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AlbumUpsert(@NotBlank @Size(max = 32) String source,
                          @NotBlank @Size(max = 128) String externalId,
                          @Size(max = 128) String sourceHash,
                          @NotNull @Positive Long artistId,
                          @NotBlank @Size(max = 200) String title,
                          @NotNull AlbumEntity.Type type,
                          List<@NotNull Genre> genres,
                          LocalDate releasedAt,
                          @Size(max = 512) String thumbnailUrl,
                          @Size(max = 100) List<@Valid @NotNull TrackUpsert> tracks,
                          List<@Valid @NotNull PhysicalAlbumUpsert> physicalAlbums,
                          @Size(max = 1000) String description) {}
