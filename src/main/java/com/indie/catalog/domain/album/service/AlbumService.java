package com.indie.catalog.domain.album.service;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.dto.response.TrackItem;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 앨범 · 트랙 조회 (C4 · C5) */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final ArtistRepository artistRepository;
    private final ProductService productService;

    public AlbumService(AlbumRepository albumRepository, TrackRepository trackRepository,
                        ArtistRepository artistRepository, ProductService productService) {
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.artistRepository = artistRepository;
        this.productService = productService;
    }

    /** C4 — 신보순 (FR-CAT-10 · 14) */
    @Transactional(readOnly = true)
    public PageResult<AlbumItem> list(AlbumEntity.Type type, int page, int size) {
        return PageResult.of(albumRepository.findLatest(type, (long) page * size, size + 1), page, size, this::albumItems);
    }

    /** C5 — 트랙(장르 · 메타데이터) · 피지컬 앨범 포함 */
    @Transactional(readOnly = true)
    public AlbumDetail detail(long id) {
        AlbumEntity album = albumRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        String artistName = artistRepository.findById(album.getArtistId()).map(ArtistEntity::getName).orElse(null);
        List<TrackItem> tracks = trackRepository.findByAlbumIdOrderByTrackNo(id).stream()
                .map(t -> new TrackItem(t.getId(), t.getTrackNo(), t.getTitle(), t.getGenres(), t.getTempo(), t.getMoods(),
                        t.getEmotions(), t.getStyles())).toList();
        return new AlbumDetail(album.getId(), album.getArtistId(), artistName, album.getTitle(), album.getType(),
                album.getGenres(), album.getReleasedAt(), album.getThumbnailUrl(), tracks,
                productService.physicalAlbumsOfAlbums(List.of(id)));
    }

    @Transactional(readOnly = true)
    public List<AlbumItem> albumsOfArtist(long artistId) {
        return albumItems(albumRepository.findByArtistId(artistId));
    }

    private List<AlbumItem> albumItems(List<AlbumEntity> albums) {
        Map<Long, String> artistNames = artistRepository.findAllById(albums.stream().map(AlbumEntity::getArtistId).distinct().toList())
                .stream().collect(Collectors.toMap(ArtistEntity::getId, ArtistEntity::getName));
        return albums.stream().map(a -> new AlbumItem(a.getId(), a.getArtistId(), artistNames.get(a.getArtistId()),
                a.getTitle(), a.getType(), a.getGenres(), a.getReleasedAt(), a.getThumbnailUrl())).toList();
    }
}
