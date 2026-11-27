package com.indie.catalog.domain.artist.controller;

import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.global.auth.CurrentUserArgumentResolver;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListSort;
import com.indie.catalog.global.common.util.PageSize;
import com.indie.catalog.global.event.UserMetrics;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 프론트 -> Gateway -> Catalog ArtistService의 아티스트 조회 API. */
@RestController
public class ArtistController {

    private final ArtistService artistService;
    private final PageSize pageSize;
    private final UserMetrics userMetrics;

    public ArtistController(ArtistService artistService, PageSize pageSize, UserMetrics userMetrics) {
        this.artistService = artistService;
        this.pageSize = pageSize;
        this.userMetrics = userMetrics;
    }

    /**
     * 조건에 맞는 아티스트 목록을 조회합니다.
     *
     * @param q 검색어
     * @param genre 장르 필터 목록
     * @param gender 아티스트 성별 필터 목록
     * @param composition 아티스트 구성 필터 목록
     * @param ids 아티스트 ID 목록
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
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

    /**
     * 아티스트를 이름과 속성으로 검색하고 회원 검색어를 기록합니다.
     *
     * @param q 검색어
     * @param genre 장르 필터 목록
     * @param gender 아티스트 성별 필터 목록
     * @param composition 아티스트 구성 필터 목록
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/api/catalog/search/artists")
    public ApiResponse<PageResult<ArtistItem>> search(@RequestParam(required = false) @Size(max = 100) String q,
                                                       @RequestParam(required = false) List<Genre> genre,
                                                       @RequestParam(required = false) List<ArtistGender> gender,
                                                       @RequestParam(required = false) List<ArtistComposition> composition,
                                                       @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestHeader(value = CurrentUserArgumentResolver.HEADER, required = false) Long userId) {
        PageResult<ArtistItem> result = artistService.list(q, genre, gender, composition, null, null, page, pageSize.resolve(size));
        userMetrics.searched(userId, "ARTIST", q, page);
        return ApiResponse.onSuccess(result);
    }

    /**
     * 아티스트 상세를 조회합니다.
     *
     * @param artistId 아티스트 ID
     */
    @GetMapping("/api/catalog/artists/{artistId}")
    public ApiResponse<ArtistDetail> detail(@PathVariable @Positive long artistId) {
        return ApiResponse.onSuccess(artistService.detail(artistId));
    }
}
