package com.indie.catalog.domain.artist.controller;

import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

    /**
     * q · genre(여러 개) · ids(그 아티스트만 · 최대 200) 를 함께 거르고 페이지로 — 11/19 팔로우 탭.
     * 여러 값은 genre=A&genre=B 또는 genre=A,B. sort 없으면 가나다순 · ARTIST_ORDER 는 ids 순서 · UPDATED 는 최근 수정순
     */
    @GetMapping("/api/catalog/artists")
    public ApiResponse<PageResult<ArtistItem>> list(@RequestParam(required = false) @Size(max = 100) String q,
                                                     @RequestParam(required = false) List<Genre> genre,
                                                     @RequestParam(required = false) List<ArtistGender> gender,
                                                     @RequestParam(required = false) List<ArtistComposition> composition,
                                                     @RequestParam(required = false) List<Long> ids,
                                                     @RequestParam(required = false) ListSort sort,
                                                     @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(artistService.list(q, genre, gender, composition, ids, sort, page, pageSize.resolve(size)));
    }

    /** 11/19 전체 검색 (프론트 요구 1-3 · 2-1) — 아티스트 이름 LIKE + 장르(여러 개 · OR). q 가 없으면 칩만으로 · 가나다순 */
    @GetMapping("/api/catalog/search/artists")
    public ApiResponse<PageResult<ArtistItem>> search(@RequestParam(required = false) @Size(max = 100) String q,
                                                       @RequestParam(required = false) List<Genre> genre,
                                                       @RequestParam(required = false) List<ArtistGender> gender,
                                                       @RequestParam(required = false) List<ArtistComposition> composition,
                                                       @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(artistService.list(q, genre, gender, composition, null, null, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/artists/{id}")
    public ApiResponse<ArtistDetail> detail(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(artistService.detail(id));
    }
}
