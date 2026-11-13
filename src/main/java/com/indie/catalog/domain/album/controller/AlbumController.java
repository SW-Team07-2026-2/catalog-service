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

/** 공개 API C4 · C5 — 앨범 */
@RestController
public class AlbumController {

    private final AlbumService albumService;
    private final PageSize pageSize;

    public AlbumController(AlbumService albumService, PageSize pageSize) {
        this.albumService = albumService;
        this.pageSize = pageSize;
    }

    @GetMapping("/api/catalog/albums")
    public ApiResponse<PageResult<AlbumItem>> list(@RequestParam(required = false) AlbumEntity.Type type,
                                                   @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                   @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(albumService.list(type, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/albums/{id}")
    public ApiResponse<AlbumDetail> detail(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(albumService.detail(id));
    }
}
