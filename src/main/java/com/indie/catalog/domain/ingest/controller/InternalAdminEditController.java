package com.indie.catalog.domain.ingest.controller;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.ingest.dto.request.AdminAlbumUpdate;
import com.indie.catalog.domain.ingest.dto.request.AdminArtistUpdate;
import com.indie.catalog.domain.ingest.service.AdminEditService;
import com.indie.catalog.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자(Studio)가 넣는 값 — 아티스트 이미지 · 앨범 이미지 · 트랙 태그 (11/18). 상품 이미지는 PATCH /internal/admin/catalog/products/{id}.
 * 토큰 규칙은 다른 /internal/admin/** 와 같습니다 (iss=studio).
 */
@RestController
public class InternalAdminEditController {

    private final AdminEditService adminEditService;

    public InternalAdminEditController(AdminEditService adminEditService) {
        this.adminEditService = adminEditService;
    }

    @PatchMapping("/internal/admin/catalog/artists/{id}")
    public ApiResponse<ArtistDetail> updateArtist(@PathVariable @Positive long id, @Valid @RequestBody AdminArtistUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateArtist(id, request.profileUrl()));
    }

    @PatchMapping("/internal/admin/catalog/albums/{id}")
    public ApiResponse<AlbumDetail> updateAlbum(@PathVariable @Positive long id, @Valid @RequestBody AdminAlbumUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateAlbum(id, request));
    }
}
