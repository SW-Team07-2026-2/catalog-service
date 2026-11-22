package com.indie.catalog.domain.ingest.dto.request;

import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import jakarta.validation.constraints.Size;

/**
 * 관리자 아티스트 수정 — 보낸 값만 바꿉니다 (null 은 그대로).
 * profileUrl: Studio 가 올린 이미지 주소 (주소 확인은 Studio 가 합니다) · 11/22 gender · composition: 성별 · 구성 (목록 밖 값은 400).
 */
public record AdminArtistUpdate(@Size(max = 512) String profileUrl,
                                ArtistGender gender,
                                ArtistComposition composition) {}
