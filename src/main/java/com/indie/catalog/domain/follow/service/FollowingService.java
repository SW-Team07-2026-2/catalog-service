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
 * Member의 팔로우 목록을 기준으로 아티스트, 음반, 굿즈를 조회합니다.
 * 새소식순은 Catalog와 Concert의 최신 시각을 합쳐 정렬하며 팔로우가 없으면 빈 페이지를 반환합니다.
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

    /**
     * 팔로우 목록을 조회하고 조건과 정렬을 적용합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param genres 장르 필터 목록
     * @param genders 아티스트 성별 필터 목록
     * @param compositions 아티스트 구성 필터 목록
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    public PageResult<ArtistItem> artists(long userId, String q, List<Genre> genres, List<ArtistGender> genders,
                                          List<ArtistComposition> compositions, FollowSort sort, int page, int size) {
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        if (sort == FollowSort.UPDATED) ids = byLatestNews(ids);
        return artistService.listOf(q, genres, genders, compositions, ids, ListSort.ARTIST_ORDER, page, size);
    }

    /**
     * 팔로우한 아티스트의 피지컬 앨범을 조건별로 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param formats 피지컬 앨범 매체 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    public PageResult<PhysicalAlbumItem> physicalAlbums(long userId, String q, List<PhysicalFormat> formats, Integer minPrice,
                                                        Integer maxPrice, boolean excludeSoldOut, FollowSort sort, int page, int size) {
        ListFilters.checkPrice(minPrice, maxPrice);
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        return productService.physicalAlbums(new PhysicalAlbumRepository.Filter(formats, null, null, ids, q, minPrice, maxPrice,
                excludeSoldOut, itemSort(sort)), page, size);
    }

    /**
     * 팔로우한 아티스트의 굿즈를 조건별로 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param q 검색어
     * @param categories 굿즈 품목 필터 목록
     * @param minPrice 상품 최저가의 하한(원)
     * @param maxPrice 상품 최저가의 상한(원), 150000 이상이면 상한 없음
     * @param excludeSoldOut 품절 상품 제외 여부
     * @param sort 목록 정렬 기준
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    public PageResult<GoodsItem> goods(long userId, String q, List<GoodsCategory> categories, Integer minPrice, Integer maxPrice,
                                       boolean excludeSoldOut, FollowSort sort, int page, int size) {
        ListFilters.checkPrice(minPrice, maxPrice);
        List<Long> ids = artistOrder(follows(userId), sort);
        if (ids.isEmpty()) return empty(page, size);
        return productService.goods(new GoodsRepository.Filter(ids, categories, q, minPrice, maxPrice, excludeSoldOut, itemSort(sort)), page, size);
    }

    /** Member 는 최근 팔로우순으로 줍니다 - 팔로워순만 다시 정렬 (같은 수면 최근 팔로우순 유지) */
    private static List<Long> artistOrder(List<FollowedArtist> follows, FollowSort sort) {
        return (sort == FollowSort.FOLLOWERS
                ? follows.stream().sorted(Comparator.comparingLong(FollowedArtist::followerCount).reversed())
                : follows.stream()).map(FollowedArtist::artistId).toList();
    }

    /** 새소식 최신순 - 소식이 없는 아티스트는 뒤 (같으면 최근 팔로우순 유지). Concert 가 불통이면 공연 없이 정렬합니다 */
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
