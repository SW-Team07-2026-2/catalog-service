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
 * Studio -> Catalog 내부 관리자 API -> AdminEditService.
 * 아티스트 등록과 아티스트, 앨범 정보 수정을 처리합니다.
 */
@RestController
public class InternalAdminEditController {

    private final AdminEditService adminEditService;

    public InternalAdminEditController(AdminEditService adminEditService) {
        this.adminEditService = adminEditService;
    }

    /**
     * 관리자가 입력한 아티스트를 등록합니다.
     *
     * @param request 등록할 아티스트 정보
     */
    @PostMapping("/internal/admin/catalog/artists")
    public ApiResponse<ArtistDetail> createArtist(@Valid @RequestBody AdminArtistCreate request) {
        return ApiResponse.onSuccess(adminEditService.createArtist(request));
    }

    /**
     * 아티스트 정보를 수정합니다.
     *
     * @param artistId 아티스트 ID
     * @param request 수정할 아티스트 정보
     */
    @PatchMapping("/internal/admin/catalog/artists/{artistId}")
    public ApiResponse<ArtistDetail> updateArtist(@PathVariable @Positive long artistId, @Valid @RequestBody AdminArtistUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateArtist(artistId, request));
    }

    /**
     * 앨범과 트랙 정보를 수정합니다.
     *
     * @param albumId 앨범 ID
     * @param request 수정할 앨범과 트랙 정보
     */
    @PatchMapping("/internal/admin/catalog/albums/{albumId}")
    public ApiResponse<AlbumDetail> updateAlbum(@PathVariable @Positive long albumId, @Valid @RequestBody AdminAlbumUpdate request) {
        return ApiResponse.onSuccess(adminEditService.updateAlbum(albumId, request));
    }
}
