package com.indie.catalog.domain.album.service;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.dto.response.LpItem;
import com.indie.catalog.domain.album.dto.response.SaleInfo;
import com.indie.catalog.domain.album.dto.response.TrackItem;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.LpEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.album.repository.LpRepository;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.response.PageResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 앨범 · 트랙 · LP 조회 (C4 · C5 · C6) */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final LpRepository lpRepository;
    private final ArtistRepository artistRepository;
    private final ProductService productService;

    public AlbumService(AlbumRepository albumRepository, TrackRepository trackRepository, LpRepository lpRepository,
                        ArtistRepository artistRepository, ProductService productService) {
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.lpRepository = lpRepository;
        this.artistRepository = artistRepository;
        this.productService = productService;
    }

    /** C4 — 신보순 (FR-CAT-10 · 14) */
    @Transactional(readOnly = true)
    public PageResult<AlbumItem> list(AlbumEntity.Type type, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return PageResult.of(type == null ? albumRepository.findLatest(pageable)
                : albumRepository.findLatestByType(type, pageable), this::albumItems);
    }

    /** C5 — 트랙 · LP · CD 판매 정보 포함 */
    @Transactional(readOnly = true)
    public AlbumDetail detail(long id) {
        AlbumEntity album = albumRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        String artistName = artistRepository.findById(album.getArtistId()).map(ArtistEntity::getName).orElse(null);
        List<TrackItem> tracks = trackRepository.findByAlbumIdOrderByTrackNo(id).stream()
                .map(t -> new TrackItem(t.getTrackNo(), t.getTitle())).toList();
        List<LpItem> lps = lpItems(lpRepository.findByAlbumIdInOrderByReleasedAtDescIdDesc(List.of(id)));
        ProductEntity cd = productService.productsByRef(ProductEntity.Type.CD, List.of(id)).get(id);
        return new AlbumDetail(album.getId(), album.getArtistId(), artistName, album.getTitle(), album.getType().name(),
                album.getReleasedAt(), album.getThumbnailUrl(), tracks, lps,
                cd == null ? null : new SaleInfo(cd.getId(), cd.getPrice(), cd.getPurchaseUrl()));
    }

    /** C6 — LP 발매 목록 (발매일 기준 · FR-CAT-12). 구간을 안 주면 전체 */
    @Transactional(readOnly = true)
    public PageResult<LpItem> lps(LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "from 은 to 보다 이후일 수 없습니다");
        }
        return PageResult.of(lpRepository.findReleased(from == null ? LocalDate.of(1900, 1, 1) : from,
                to == null ? LocalDate.of(9999, 12, 31) : to, PageRequest.of(page, size)), this::lpItems);
    }

    @Transactional(readOnly = true)
    public List<AlbumItem> albumsOfArtist(long artistId) {
        return albumItems(albumRepository.findByArtistIdOrderByReleasedAtDescIdDesc(artistId));
    }

    @Transactional(readOnly = true)
    public List<LpItem> lpsOfAlbums(Collection<Long> albumIds) {
        return albumIds.isEmpty() ? List.of() : lpItems(lpRepository.findByAlbumIdInOrderByReleasedAtDescIdDesc(albumIds));
    }

    private List<AlbumItem> albumItems(List<AlbumEntity> albums) {
        Map<Long, String> artistNames = artistNames(albums.stream().map(AlbumEntity::getArtistId).toList());
        return albums.stream().map(a -> new AlbumItem(a.getId(), a.getArtistId(), artistNames.get(a.getArtistId()),
                a.getTitle(), a.getType().name(), a.getReleasedAt(), a.getThumbnailUrl())).toList();
    }

    private List<LpItem> lpItems(List<LpEntity> lps) {
        Map<Long, AlbumEntity> albums = albumRepository.findAllById(lps.stream().map(LpEntity::getAlbumId).distinct().toList())
                .stream().collect(Collectors.toMap(AlbumEntity::getId, Function.identity()));
        Map<Long, String> artistNames = artistNames(albums.values().stream().map(AlbumEntity::getArtistId).toList());
        Map<Long, ProductEntity> products = productService.productsByRef(ProductEntity.Type.LP, lps.stream().map(LpEntity::getId).toList());
        return lps.stream().map(l -> {
            AlbumEntity album = albums.get(l.getAlbumId());
            ProductEntity p = products.get(l.getId());
            return new LpItem(l.getId(), l.getAlbumId(), album.getTitle(), album.getArtistId(), artistNames.get(album.getArtistId()),
                    l.getPressType(), l.getReleasedAt(), album.getThumbnailUrl(),
                    p == null ? null : p.getId(), p == null ? null : p.getPrice());
        }).toList();
    }

    private Map<Long, String> artistNames(List<Long> artistIds) {
        return artistRepository.findAllById(artistIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(ArtistEntity::getId, ArtistEntity::getName));
    }
}
