package com.indie.catalog.domain.ingest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 아티스트 이미지 바꾸기 — Studio 가 올린 이미지 주소 (주소 확인은 Studio 가 합니다) */
public record AdminArtistUpdate(@NotBlank @Size(max = 512) String profileUrl) {}
