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
 * 11/26 관리자 아티스트 등록 — 공연 라인업에 있는데 수집되지 않은 팀 (MusicBrainz 에 없음 · 크롤러가 못 찾음).
 * name 만 꼭 · 나머지는 null 이면 빈 값. genres 는 정해진 값만 (Genre) · profileUrl 은 Studio 가 올린 이미지 주소 (주소 확인은 Studio).
 * 같은 이름(앞뒤 공백 · 대소문자 무시)의 아티스트가 있으면 409 — 같은 이름의 다른 팀이면 allowSameName: true.
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
