package com.indie.catalog.domain.follow.controller;

import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.follow.dto.request.FollowSort;
import com.indie.catalog.domain.follow.service.FollowingService;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.global.auth.CurrentUser;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.PageSize;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인한 프론트 -> Gateway -> Catalog FollowingService -> Member와 Concert 조회.
 * 팔로우한 아티스트의 목록, 음반, 굿즈를 조건별로 반환합니다.
 */
@RestController
public class FollowingController {

    private final FollowingService followingService;
    private final PageSize pageSize;

    public FollowingController(FollowingService followingService, PageSize pageSize) {
        this.followingService = followingService;
        this.pageSize = pageSize;
    }

    /**
     * 팔로우한 아티스트 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param genre 장르 필터 목록
     * @param gender 아티스트 성별 필터 목록
     * @param composition 아티스트 구성 필터 목록
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/following/artists")
    public ApiResponse<PageResult<ArtistItem>> artists(@CurrentUser Long userId,
                                                        @RequestParam(required = false) @Size(max = 100) String q,
                                                        @RequestParam(required = false) List<Genre> genre,
                                                        @RequestParam(required = false) List<ArtistGender> gender,
                                                        @RequestParam(required = false) List<ArtistComposition> composition,
                                                        @RequestParam(required = false) FollowSort sort,
                                                        @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.artists(userId, q, genre, gender, composition, sort, page, pageSize.resolve(size)));
    }

    /**
     * 팔로우한 아티스트의 피지컬 앨범을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param format 피지컬 앨범 매체 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/following/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> physicalAlbums(@CurrentUser Long userId,
                                                                    @RequestParam(required = false) @Size(max = 100) String q,
                                                                    @RequestParam(required = false) List<PhysicalFormat> format,
                                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                                    @RequestParam(defaultValue = "false") boolean excludeSoldOut,
                                                                    @RequestParam(required = false) FollowSort sort,
                                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.physicalAlbums(userId, q, format, minPrice, maxPrice, excludeSoldOut, sort,
                page, pageSize.resolve(size)));
    }

    /**
     * 팔로우한 아티스트의 굿즈를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param category 굿즈 품목 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/api/catalog/following/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@CurrentUser Long userId,
                                                    @RequestParam(required = false) @Size(max = 100) String q,
                                                    @RequestParam(required = false) List<GoodsCategory> category,
                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                    @RequestParam(defaultValue = "false") boolean excludeSoldOut,
                                                    @RequestParam(required = false) FollowSort sort,
                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.goods(userId, q, category, minPrice, maxPrice, excludeSoldOut, sort, page, pageSize.resolve(size)));
    }
}
