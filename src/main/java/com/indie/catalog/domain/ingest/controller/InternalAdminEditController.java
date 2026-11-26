package com.indie.catalog.domain.ingest.controller;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.ingest.dto.request.AdminAlbumUpdate;
import com.indie.catalog.domain.ingest.dto.request.AdminArtistCreate;
import com.indie.catalog.domain.ingest.dto.request.AdminArtistUpdate;
import com.indie.catalog.domain.ingest.service.AdminEditService;
import com.indie.catalog.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자(Studio)가 넣는 값 — 아티스트 이미지 · 성별 · 구성(11/22) · 앨범 이미지 · 트랙 태그 (11/18). 상품 이미지는 PATCH /internal/admin/catalog/products/{productId}.
 * 11/26 아티스트 등록 — 공연 라인업에 있는데 수집되지 않은 팀을 관리자가 직접 (source = studio).
 * 토큰 규칙은 다른 /internal/admin/** 와 같습니다 (iss=studio).
 */
@RestController
public class InternalAdminEditController {

    private final AdminEditService adminEditService;

    public InternalAdminEditController(AdminEditService adminEditService) {
        this.adminEditService = adminEditService;
    }

    @PostMapping("/internal/admin/catalog/artists")
    public ApiResponse<ArtistDetail> createArtist(@Valid @RequestBody AdminArtistCreate request) {
        return ApiResponse.onSuccess(adminEditService.createArtist(request));
    }

    @PatchMapping("/internal/admin/catalog/artists/{artistId}")
    public ApiResponse<ArtistDetail> updateArtist(@PathVariable @Positive long artistId, @Valid @RequestBody AdminArtistUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateArtist(artistId, request));
    }

    @PatchMapping("/internal/admin/catalog/albums/{albumId}")
    public ApiResponse<AlbumDetail> updateAlbum(@PathVariable @Positive long albumId, @Valid @RequestBody AdminAlbumUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateAlbum(albumId, request));
    }
}
