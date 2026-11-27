package com.indie.catalog.domain.ingest.dto.request;

import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import jakarta.validation.constraints.Size;

/**
 * 관리자 아티스트 수정 - 보낸 값만 바꿉니다 (null 은 그대로).
 * profileUrl: Studio 가 올린 이미지 주소 (주소 확인은 Studio 가 합니다), gender, composition: 성별, 구성 (목록 밖 값은 400).
 * bio: 소개 글 (빈 글은 지움).
 */
public record AdminArtistUpdate(@Size(max = 512) String profileUrl,
                                ArtistGender gender,
                                ArtistComposition composition,
                                @Size(max = 1000) String bio) {}
