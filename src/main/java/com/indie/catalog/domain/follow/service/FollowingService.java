package com.indie.catalog.domain.follow.service;

import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.ArtistComposition;
import com.indie.catalog.domain.artist.entity.ArtistGender;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.domain.follow.client.ConcertClient;
import com.indie.catalog.domain.follow.client.MemberClient;
import com.indie.catalog.domain.follow.client.MemberClient.FollowedArtist;
import com.indie.catalog.domain.follow.dto.request.FollowSort;
import com.indie.catalog.domain.follow.exception.FollowErrorCode;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.repository.PhysicalAlbumRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListFilters;
import com.indie.catalog.global.common.util.ListSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 팔로우 탭 (11/19 · 프론트 요구 1-1 · 1-2 · 1-3) — 로그인 회원이 팔로우한 아티스트(의 앨범 · 굿즈)만, 칩 · 검색어 · 가격대를 함께.
 * 팔로우 목록은 Member 내부 API 로 받고(최근 팔로우순 · 팔로워 수), 정렬은 아티스트 순서(ARTIST_ORDER)나 최근 수정순(UPDATED)으로 바꿔
 * 같은 목록 쿼리에 넘깁니다. 팔로우가 없으면 DB 를 부르지 않고 빈 페이지.
 * 아티스트 목록 업데이트순은 아티스트별 가장 최근 새소식 날짜 — 앨범 · 피지컬 발매일 · 굿즈 등록 시각(Catalog)과 공연 등록 · 수정 시각(Concert) 중 가장 늦은 것.
 */
@Service
public class FollowingService {

    private static final Logger log = LoggerFactory.getLogger(FollowingService.class);

    private final MemberClient memberClient;
    private final ConcertClient concertClient;
    private final ArtistService artistService;
    private final ProductService productService;

    public FollowingService(MemberClient memberClient, ConcertClient concertClient, ArtistService artistService,
                            ProductService productService) {
        this.memberClient = memberClient;
        this.concertClient = concertClient;
        this.artistService = artistService;
        this.productService = productService;
    }

    public PageResult<ArtistItem> artists(long userId, String q, List<Genre> genres, List<ArtistGender> genders,
                                          List<ArtistComposition> compositions, FollowSort sort, int page, int size) {
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        if (sort == FollowSort.UPDATED) ids = byLatestNews(ids);
        return artistService.listOf(q, genres, genders, compositions, ids, ListSort.ARTIST_ORDER, page, size);
    }

    public PageResult<PhysicalAlbumItem> physicalAlbums(long userId, String q, List<PhysicalFormat> formats, Integer minPrice,
                                                        Integer maxPrice, boolean excludeSoldOut, FollowSort sort, int page, int size) {
        ListFilters.checkPrice(minPrice, maxPrice);
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        return productService.physicalAlbums(new PhysicalAlbumRepository.Filter(formats, null, null, ids, q, minPrice, maxPrice,
                excludeSoldOut, itemSort(sort)), page, size);
    }

    public PageResult<GoodsItem> goods(long userId, String q, List<GoodsCategory> categories, Integer minPrice, Integer maxPrice,
                                       boolean excludeSoldOut, FollowSort sort, int page, int size) {
        ListFilters.checkPrice(minPrice, maxPrice);
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        return productService.goods(new GoodsRepository.Filter(ids, categories, q, minPrice, maxPrice, excludeSoldOut, itemSort(sort)), page, size);
    }

    /** Member 는 최근 팔로우순으로 줍니다 — 팔로워순만 다시 정렬 (같은 수면 최근 팔로우순 유지) */
    private static List<Long> artistOrder(List<FollowedArtist> follows, FollowSort sort) {
        return (sort == FollowSort.FOLLOWERS
                ? follows.stream().sorted(Comparator.comparingLong(FollowedArtist::followerCount).reversed())
                : follows.stream()).map(FollowedArtist::artistId).toList();
    }

    /** 새소식 최신순 — 소식이 없는 아티스트는 뒤 (같으면 최근 팔로우순 유지). Concert 가 불통이면 공연 없이 정렬합니다 */
    private List<Long> byLatestNews(List<Long> ids) {
        Map<Long, OffsetDateTime> latest = new HashMap<>(artistService.latestContentAt(ids));
        try {
            concertClient.latestConcertAt(ids).forEach((id, at) -> latest.merge(id, at, (x, y) -> x.isAfter(y) ? x : y));
        } catch (ConcertClient.Unavailable e) {
            log.warn("following_latest_concert_unavailable error={}", e.getMessage());
        }
        return ids.stream().sorted(Comparator.comparing(latest::get, Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())))
                .toList();
    }

    private static ListSort itemSort(FollowSort sort) {
        return sort == FollowSort.UPDATED ? ListSort.UPDATED : ListSort.ARTIST_ORDER;
    }

    private List<FollowedArtist> follows(long userId) {
        try {
            return memberClient.follows(userId);
        } catch (MemberClient.Unavailable e) {
            throw new GeneralException(FollowErrorCode.FOLLOW_UNAVAILABLE);
        }
    }

    private static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), page, size, false);
    }
}
