package com.indie.catalog.domain.follow.controller;

import com.indie.catalog.domain.artist.dto.response.ArtistItem;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 11/19 팔로우 탭 (프론트 요구 1-1 · 1-2 · 1-3) — 로그인 필요 · 내가 팔로우한 아티스트(의 앨범 · 굿즈)만.
 * 검색어 q 는 선택 (없으면 칩만으로). 칩은 여러 개(genre=A&genre=B 또는 genre=A,B · 같은 칩 안은 OR, 서로 다른 칩끼리는 AND).
 * sort — RECENT_FOLLOW(기본) · FOLLOWERS · UPDATED. 가격은 목록 최저가(원) · maxPrice ≥ 150,000 은 상한 없음.
 */
@RestController
public class FollowingController {

    private final FollowingService followingService;
    private final PageSize pageSize;

    public FollowingController(FollowingService followingService, PageSize pageSize) {
        this.followingService = followingService;
        this.pageSize = pageSize;
    }

    @GetMapping("/api/catalog/following/artists")
    public ApiResponse<PageResult<ArtistItem>> artists(@CurrentUser Long userId,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) List<Genre> genre,
                                                        @RequestParam(required = false) FollowSort sort,
                                                        @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.artists(userId, q, genre, sort, page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/following/physical-albums")
    public ApiResponse<PageResult<PhysicalAlbumItem>> physicalAlbums(@CurrentUser Long userId,
                                                                    @RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) List<PhysicalFormat> format,
                                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                                    @RequestParam(required = false) FollowSort sort,
                                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.physicalAlbums(userId, q, format, minPrice, maxPrice, sort,
                page, pageSize.resolve(size)));
    }

    @GetMapping("/api/catalog/following/goods")
    public ApiResponse<PageResult<GoodsItem>> goods(@CurrentUser Long userId,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) List<GoodsCategory> category,
                                                    @RequestParam(required = false) @PositiveOrZero Integer minPrice,
                                                    @RequestParam(required = false) @PositiveOrZero Integer maxPrice,
                                                    @RequestParam(required = false) FollowSort sort,
                                                    @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followingService.goods(userId, q, category, minPrice, maxPrice, sort, page, pageSize.resolve(size)));
    }
}
