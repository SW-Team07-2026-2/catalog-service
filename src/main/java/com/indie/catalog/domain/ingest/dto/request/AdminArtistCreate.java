package com.indie.catalog.domain.ingest.dto.request;

import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자 아티스트 등록 요청. 이름은 필수이고 장르는 정해진 값만 허용합니다.
 * 동일 이름이 있으면 409를 반환하며 allowSameName=true이면 중복 이름을 허용합니다.
 */
public record AdminArtistCreate(@NotBlank @Size(max = 100) String name,
                                List<@NotNull Genre> genres,
                                @Size(max = 100) String label,
                                @Min(1900) @Max(2100) Integer debutYear,
                                @Size(max = 512) String profileUrl,
                                ArtistGender gender,
                                ArtistComposition composition,
                                @Size(max = 1000) String bio,
                                Boolean allowSameName) {}
