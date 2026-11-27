package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.indie.catalog.domain.album.entity.Emotion;
import com.indie.catalog.domain.album.entity.Mood;
import com.indie.catalog.domain.album.entity.Style;
import com.indie.catalog.domain.album.entity.Tempo;
import com.indie.catalog.domain.artist.entity.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 장르, 메타데이터는 정해진 값만 - null 이면 저장된 값 그대로(새 트랙은 빈 값), 빈 배열이면 지움 (: 관리자가 넣는 값) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrackUpsert(@NotNull @Positive Integer trackNo,
                          @NotBlank @Size(max = 200) String title,
                          List<@NotNull Genre> genres,
                          Tempo tempo,
                          List<@NotNull Mood> moods,
                          List<@NotNull Emotion> emotions,
                          List<@NotNull Style> styles) {}
