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

/** 앨범, 트랙 조회 (C4, C5) */
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

    /**
     * 유형별 앨범 목록을 조회합니다.
     *
     * @param type 앨범 유형 필터
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지당 조회 개수
     */
    @Transactional(readOnly = true)
    public PageResult<AlbumItem> list(AlbumEntity.Type type, int page, int size) {
        return PageResult.of(albumRepository.findLatest(type, (long) page * size, size + 1), page, size, this::albumItems);
    }

    /**
     * 앨범 상세와 트랙을 조회합니다.
     *
     * @param id 앨범 ID
     */
    @Transactional(readOnly = true)
    public AlbumDetail detail(long id) {
        AlbumEntity album = albumRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        String artistName = artistRepository.findById(album.getArtistId()).map(ArtistEntity::getName).orElse(null);
        List<TrackItem> tracks = trackRepository.findByAlbumIdOrderByTrackNo(id).stream()
                .map(t -> new TrackItem(t.getId(), t.getTrackNo(), t.getTitle(), t.getGenres(), t.getTempo(), t.getMoods(),
                        t.getEmotions(), t.getStyles())).toList();
        return new AlbumDetail(album.getId(), album.getArtistId(), artistName, album.getTitle(), album.getType(),
                album.getGenres(), album.getReleasedAt(), album.getThumbnailUrl(), album.getDescription(), tracks,
                productService.physicalAlbumsOfAlbums(List.of(id)));
    }

    /**
     * 아티스트의 앨범 목록을 조회합니다.
     *
     * @param artistId 아티스트 ID
     */
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
