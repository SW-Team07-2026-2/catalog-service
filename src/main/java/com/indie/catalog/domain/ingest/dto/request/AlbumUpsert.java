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
 * artist_id 는 Catalog id — 수집기는 아티스트 적재 응답의 id 를 씁니다.
 * tracks 가 있으면 track_no 기준으로 맞춥니다 (같은 번호는 갱신 · 없는 번호는 삭제) · null 이면 건드리지 않습니다.
 * physical_albums 가 null 이면 피지컬 앨범은 건드리지 않습니다.
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
                          List<@Valid @NotNull PhysicalAlbumUpsert> physicalAlbums) {}
