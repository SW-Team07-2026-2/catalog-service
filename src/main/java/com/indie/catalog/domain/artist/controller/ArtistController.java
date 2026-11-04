package com.indie.catalog.domain.artist.controller;

import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 공개 API C2 · C3 — 아티스트 */
@RestController
public class ArtistController {

    private final ArtistService artistService;
    private final PageSize pageSize;

    public ArtistController(ArtistService artistService, PageSize pageSize) {
        this.artistService = artistService;
        this.pageSize = pageSize;
    }

    /** ?ids=1,2,3 이 있으면 일괄 조회 (팔로우 목록 화면), 없으면 q · genre 필터 목록 */
    @GetMapping("/api/catalog/artists")
    public ApiResponse<PageResult<ArtistItem>> list(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String genre,
                                                     @RequestParam(required = false) List<Long> ids,
                                                     @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                     @RequestParam(required = false) Integer size) {
        if (ids != null) {
            pageSize.checkIds(ids);
            return ApiResponse.onSuccess(PageResult.all(artistService.byIds(ids)));
        }
        return ApiResponse.onSuccess(artistService.list(q, genre, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/artists/{id}")
    public ApiResponse<ArtistDetail> detail(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(artistService.detail(id));
    }
}
