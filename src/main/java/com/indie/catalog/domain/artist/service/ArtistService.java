package com.indie.catalog.domain.artist.service;

import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.service.AlbumService;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.artist.entity.LabelEntity;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.artist.repository.LabelRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import com.indie.catalog.global.common.util.ListFilters;
import com.indie.catalog.global.common.util.ListSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 아티스트 조회 (C2 · C3) */
@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final LabelRepository labelRepository;
    private final AlbumService albumService;
    private final ProductService productService;
    private final ArtistCache cache;

    public ArtistService(ArtistRepository artistRepository, LabelRepository labelRepository, AlbumService albumService,
                         ProductService productService, ArtistCache cache) {
        this.artistRepository = artistRepository;
        this.labelRepository = labelRepository;
        this.albumService = albumService;
        this.productService = productService;
        this.cache = cache;
    }

    /** C2 — 이름 · 장르(여러 개) · ids 필터 (FR-CAT-01 · 02). 기본 이름순 · 11/19 sort. 관련도 검색은 C1 */
    @Transactional(readOnly = true)
    public PageResult<ArtistItem> list(String q, List<Genre> genres, List<Long> ids, ListSort sort, int page, int size) {
        List<Long> artistIds = ListFilters.artistIds(ids, sort);
        return PageResult.of(artistRepository.search(q, genres, artistIds, sort, (long) page * size, size + 1), page, size,
                artists -> artists.stream().map(ArtistService::item).toList());
    }

    /** C3 — 프로필 + 앨범 · 피지컬 앨범 · 굿즈. 캐시 10분 (재고 제외) */
    public ArtistDetail detail(long id) {
        return cache.get(id).orElseGet(() -> {
            ArtistDetail detail = load(id);
            cache.put(detail);
            return detail;
        });
    }

    private ArtistDetail load(long id) {
        ArtistEntity a = artistRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        String label = a.getLabelId() == null ? null : labelRepository.findById(a.getLabelId()).map(LabelEntity::getName).orElse(null);
        List<AlbumItem> albums = albumService.albumsOfArtist(id);
        return new ArtistDetail(a.getId(), a.getName(), a.getGenres(), label, a.getDebutYear(), a.getProfileUrl(), albums,
                productService.physicalAlbumsOfAlbums(albums.stream().map(AlbumItem::id).toList()),
                productService.goodsOfArtist(id));
    }

    private static ArtistItem item(ArtistEntity a) {
        return new ArtistItem(a.getId(), a.getName(), a.getGenres(), a.getDebutYear(), a.getProfileUrl());
    }
}
