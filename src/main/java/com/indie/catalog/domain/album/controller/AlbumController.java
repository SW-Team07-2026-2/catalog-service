package com.indie.catalog.domain.album.controller;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.service.AlbumService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트 -> Gateway -> Catalog AlbumService의 앨범과 트랙 조회 API. */
@RestController
public class AlbumController {

    private final AlbumService albumService;
    private final PageSize pageSize;

    public AlbumController(AlbumService albumService, PageSize pageSize) {
        this.albumService = albumService;
        this.pageSize = pageSize;
    }

    /**
     * 유형별 앨범 목록을 조회합니다.
     *
     * @param type 앨범 유형 필터
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/albums")
    public ApiResponse<PageResult<AlbumItem>> list(@RequestParam(required = false) AlbumEntity.Type type,
                                                   @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(albumService.list(type, page, pageSize.resolve(size)));
    }

    /**
     * 앨범 상세와 트랙을 조회합니다.
     *
     * @param albumId 앨범 ID
     */
    @GetMapping("/api/catalog/albums/{albumId}")
    public ApiResponse<AlbumDetail> detail(@PathVariable @Positive long albumId) {
        return ApiResponse.onSuccess(albumService.detail(albumId));
    }
}
