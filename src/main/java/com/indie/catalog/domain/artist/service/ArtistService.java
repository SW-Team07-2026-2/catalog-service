package com.indie.catalog.domain.artist.service;

import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.dto.response.LpItem;
import com.indie.catalog.domain.album.service.AlbumService;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.dto.response.ArtistItem;
import com.indie.catalog.domain.artist.dto.response.MemberItem;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.LabelEntity;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.artist.repository.BandMemberRepository;
import com.indie.catalog.domain.artist.repository.LabelRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 아티스트 조회 (C2 · C3) */
@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final LabelRepository labelRepository;
    private final BandMemberRepository bandMemberRepository;
    private final AlbumService albumService;
    private final ProductService productService;
    private final ArtistCache cache;

    public ArtistService(ArtistRepository artistRepository, LabelRepository labelRepository,
                         BandMemberRepository bandMemberRepository, AlbumService albumService,
                         ProductService productService, ArtistCache cache) {
        this.artistRepository = artistRepository;
        this.labelRepository = labelRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.albumService = albumService;
        this.productService = productService;
        this.cache = cache;
    }

    /** C2 — 이름 · 장르 필터 · 이름순 (FR-CAT-01 · 02). 관련도 검색은 C1 */
    @Transactional(readOnly = true)
    public PageResult<ArtistItem> list(String q, String genre, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name", "id"));
        return PageResult.of(artistRepository.search(q, genre, pageable), artists -> artists.stream().map(ArtistService::item).toList());
    }

    /** ?ids= — 팔로우 목록 화면 (Member 팔로우는 artistId 만 들고 있음) */
    @Transactional(readOnly = true)
    public List<ArtistItem> byIds(List<Long> ids) {
        return artistRepository.findAllById(ids).stream().map(ArtistService::item).toList();
    }

    /** C3 — 프로필 + 멤버 + 앨범 · LP · 굿즈. 캐시 10분 (재고 제외) */
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
        List<MemberItem> members = bandMemberRepository.findByArtistIdOrderById(id).stream()
                .map(m -> new MemberItem(m.getName(), m.getRole())).toList();
        List<AlbumItem> albums = albumService.albumsOfArtist(id);
        List<LpItem> lps = albumService.lpsOfAlbums(albums.stream().map(AlbumItem::id).toList());
        return new ArtistDetail(a.getId(), a.getName(), a.getGenre(), label, a.getDebutYear(), a.getProfileUrl(),
                members, albums, lps, productService.goodsOfArtist(id));
    }

    private static ArtistItem item(ArtistEntity a) {
        return new ArtistItem(a.getId(), a.getName(), a.getGenre(), a.getDebutYear(), a.getProfileUrl());
    }
}
