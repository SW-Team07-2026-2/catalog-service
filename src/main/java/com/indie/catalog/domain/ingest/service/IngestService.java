package com.indie.catalog.domain.ingest.service;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.TrackEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.LabelEntity;
import com.indie.catalog.domain.artist.exception.ArtistErrorCode;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.artist.repository.LabelRepository;
import com.indie.catalog.domain.ingest.dto.event.CatalogEvents;
import com.indie.catalog.domain.ingest.dto.request.AlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.ArtistUpsert;
import com.indie.catalog.domain.ingest.dto.request.DiffItem;
import com.indie.catalog.domain.ingest.dto.request.DiffRequest;
import com.indie.catalog.domain.ingest.dto.request.GoodsUpsert;
import com.indie.catalog.domain.ingest.dto.request.OptionUpsert;
import com.indie.catalog.domain.ingest.dto.request.PhysicalAlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.TrackUpsert;
import com.indie.catalog.domain.ingest.dto.response.DiffResult;
import com.indie.catalog.domain.ingest.dto.response.ItemResult;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.repository.PhysicalAlbumRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.util.SourceHash;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 수집 데이터를 (source, external_id) 기준으로 항목별 트랜잭션에서 적재합니다.
 * 실제 변경에만 이벤트를 발행하며 신규 발매 알림은 발매 기간 설정을 따릅니다.
 * 이미지와 트랙 태그가 null이면 기존 값을 유지하고 빈 태그 배열이면 삭제합니다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ArtistRepository artistRepository;
    private final LabelRepository labelRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final PhysicalAlbumRepository physicalAlbumRepository;
    private final GoodsRepository goodsRepository;
    private final ProductService productService;
    private final CatalogEvents events;
    private final ArtistCache artistCache;
    private final TransactionTemplate tx;
    private final int releaseWindowDays;
    private final Clock clock;

    public IngestService(ArtistRepository artistRepository, LabelRepository labelRepository, AlbumRepository albumRepository,
                         TrackRepository trackRepository, PhysicalAlbumRepository physicalAlbumRepository,
                         GoodsRepository goodsRepository, ProductService productService, CatalogEvents events,
                         ArtistCache artistCache, TransactionTemplate tx, CatalogProperties props, Clock clock) {
        this.artistRepository = artistRepository;
        this.labelRepository = labelRepository;
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.physicalAlbumRepository = physicalAlbumRepository;
        this.goodsRepository = goodsRepository;
        this.productService = productService;
        this.events = events;
        this.artistCache = artistCache;
        this.tx = tx;
        this.releaseWindowDays = props.event().releaseWindowDays();
        this.clock = clock;
    }

    /**
     * 아티스트를 항목별로 적재하고 처리 결과를 반환합니다.
     *
     * @param items 적재할 아티스트 목록
     */
    public List<ItemResult> upsertArtists(List<ArtistUpsert> items) {
        return items.stream().map(item -> eachInTx(item.externalId(), () -> upsertArtist(item))).toList();
    }

    /**
     * 앨범을 항목별로 적재하고 처리 결과를 반환합니다.
     *
     * @param items 적재할 앨범 목록
     */
    public List<ItemResult> upsertAlbums(List<AlbumUpsert> items) {
        return items.stream().map(item -> eachInTx(item.externalId(), () -> upsertAlbum(item))).toList();
    }

    /**
     * I15 - 수집기는 "뭐가 새로운지"만 묻고, 판정 기준(해시 비교)은 여기 한 곳에만 둡니다
     *
     * @param request 저장된 해시와 비교할 수집 항목
     */
    public DiffResult diff(DiffRequest request) {
        List<String> ids = request.items().stream().map(DiffItem::externalId).distinct().toList();
        List<SourceHash> rows = switch (request.type()) {
            case ARTIST -> artistRepository.findHashes(request.source(), ids);
            case ALBUM -> albumRepository.findHashes(request.source(), ids);
            case PHYSICAL_ALBUM -> physicalAlbumRepository.findHashes(request.source(), ids);
            case GOODS -> goodsRepository.findHashes(request.source(), ids);
        };
        Map<String, String> stored = new HashMap<>();
        rows.forEach(r -> stored.put(r.externalId(), r.sourceHash()));
        List<String> created = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        for (DiffItem item : request.items()) {
            if (!stored.containsKey(item.externalId())) created.add(item.externalId());
            else if (item.sourceHash() != null && item.sourceHash().equals(stored.get(item.externalId()))) unchanged.add(item.externalId());
            else changed.add(item.externalId());
        }
        return new DiffResult(created, changed, unchanged);
    }

    private ItemResult eachInTx(String externalId, Supplier<ItemResult> work) {
        try {
            return tx.execute(s -> work.get());
        } catch (GeneralException e) {
            return ItemResult.failed(externalId, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // 같은 external_id 를 두 요청이 동시에 처음 넣은 경우 - 다시 보내면 UPDATE 경로로 성공합니다
            log.warn("ingest_conflict externalId={} error={}", externalId, e.getMostSpecificCause().getClass().getSimpleName());
            return ItemResult.failed(externalId, "동시 적재 충돌 — 다시 시도하세요");
        }
    }

    private ItemResult upsertArtist(ArtistUpsert u) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ArtistEntity artist = artistRepository.findBySourceAndExternalId(u.source(), u.externalId()).orElse(null);
        if (artist != null && sameHash(u.sourceHash(), artist.getSourceHash())) {
            return new ItemResult(u.externalId(), artist.getId(), ItemResult.Result.UNCHANGED, null);
        }
        boolean created = artist == null;
        if (created) artist = new ArtistEntity(u.source(), u.externalId());
        Long labelId = u.label() == null || u.label().isBlank() ? null : labelRepository.findByName(u.label().strip())
                .orElseGet(() -> labelRepository.save(new LabelEntity(u.label().strip()))).getId();
        boolean changed = artist.apply(u.name().strip(), distinct(u.genres()), labelId, u.debutYear(),
                keep(u.profileUrl(), artist.getProfileUrl()), u.gender(), u.composition(), u.sourceHash(), now);
        changed |= artist.getBio() == null && artist.applyBio(u.bio(), now);   // 비어 있을 때만 채움 (관리자가 쓴 글은 수집으로 안 바뀜)
        artist = artistRepository.save(artist);
        if (created || changed) events.artistUpdated(artist);

        boolean goodsChanged = false;
        if (u.goods() != null) {
            for (GoodsUpsert g : u.goods()) goodsChanged |= upsertGoods(artist, u.source(), g, now);
        }
        if (created || changed || goodsChanged) artistCache.evictAfterCommit(artist.getId());
        return new ItemResult(u.externalId(), artist.getId(), created ? ItemResult.Result.CREATED
                : changed || goodsChanged ? ItemResult.Result.UPDATED : ItemResult.Result.UNCHANGED, null);
    }

    private boolean upsertGoods(ArtistEntity artist, String source, GoodsUpsert g, OffsetDateTime now) {
        GoodsEntity goods = goodsRepository.findBySourceAndExternalId(source, g.externalId())
                .orElseGet(() -> new GoodsEntity(source, g.externalId()));
        boolean created = goods.getId() == null;
        boolean changed = goods.apply(artist.getId(), g.category(), g.name().strip(), keep(g.thumbnailUrl(), goods.getThumbnailUrl()),
                g.sourceHash(), now);
        changed |= fillDetail(goods, g.description(), now);
        goods = goodsRepository.save(goods);
        ProductService.OptionsResult options = productService.upsertOptions(goods.getId(), null, inputs(g.options()));
        if (created) {
            events.goodsReleased(goods, artist, g.options().stream().map(OptionUpsert::price).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).min().orElseThrow());   // 새 상품은 옵션마다 판매가가 있어야 저장됩니다 (upsertOptions)
        }
        return created || changed || options.changed();
    }

    private ItemResult upsertAlbum(AlbumUpsert u) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ArtistEntity artist = artistRepository.findById(u.artistId())
                .orElseThrow(() -> new GeneralException(ArtistErrorCode.ARTIST_NOT_FOUND, "artist_id " + u.artistId() + " 가 없습니다"));
        AlbumEntity album = albumRepository.findBySourceAndExternalId(u.source(), u.externalId()).orElse(null);
        if (album != null && sameHash(u.sourceHash(), album.getSourceHash())) {
            return new ItemResult(u.externalId(), album.getId(), ItemResult.Result.UNCHANGED, null);
        }
        boolean created = album == null;
        if (created) album = new AlbumEntity(u.source(), u.externalId());
        Long previousArtistId = album.getArtistId();
        List<String> changed = album.apply(artist.getId(), u.title().strip(), u.type(), distinct(u.genres()), u.releasedAt(),
                keep(u.thumbnailUrl(), album.getThumbnailUrl()), u.sourceHash(), now);
        if (album.getDescription() == null && album.applyDescription(u.description(), now)) changed.add("description");   // 비어 있을 때만 채움
        album = albumRepository.save(album);

        if (u.tracks() != null && syncTracks(album.getId(), u.tracks())) changed.add("tracks");
        if (u.physicalAlbums() != null) {
            boolean physicalChanged = false;
            for (PhysicalAlbumUpsert p : u.physicalAlbums()) physicalChanged |= upsertPhysicalAlbum(album, artist, u.source(), p, now);
            if (physicalChanged) changed.add("physicalAlbums");
        }

        if (created) {
            if (recent(album.getReleasedAt())) events.albumReleased(album, artist.getName());
        } else if (!changed.isEmpty()) {
            events.albumUpdated(album, artist.getName(), changed);
        }
        if (created || !changed.isEmpty()) {
            artistCache.evictAfterCommit(artist.getId());
            if (previousArtistId != null && !previousArtistId.equals(artist.getId())) artistCache.evictAfterCommit(previousArtistId);
        }
        return new ItemResult(u.externalId(), album.getId(), created ? ItemResult.Result.CREATED
                : changed.isEmpty() ? ItemResult.Result.UNCHANGED : ItemResult.Result.UPDATED, null);
    }

    /**
     * 트랙을 track_no 기준으로 맞춥니다 - 같은 번호는 행을 유지한 채 값만 바꾸고, 요청에 없는 번호는 지우고, 새 번호는 넣습니다.
     * 통째로 지우고 다시 넣지 않는 이유: 트랙 id 를 Member 행동 메트릭이 들고 있습니다.
     */
    private boolean syncTracks(long albumId, List<TrackUpsert> tracks) {
        if (tracks.stream().map(TrackUpsert::trackNo).distinct().count() != tracks.size()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "track_no 가 중복됩니다");
        }
        Map<Integer, TrackEntity> existing = trackRepository.findByAlbumIdOrderByTrackNo(albumId).stream()
                .collect(Collectors.toMap(TrackEntity::getTrackNo, Function.identity()));
        boolean changed = false;
        for (TrackUpsert t : tracks) {
            TrackEntity track = existing.remove(t.trackNo());
            boolean isNew = track == null;
            if (isNew) track = new TrackEntity(albumId, t.trackNo());
            changed |= track.apply(t.title().strip(), tags(t.genres(), track.getGenres()), keep(t.tempo(), track.getTempo()),
                    tags(t.moods(), track.getMoods()), tags(t.emotions(), track.getEmotions()), tags(t.styles(), track.getStyles())) || isNew;
            trackRepository.save(track);
        }
        if (!existing.isEmpty()) {
            trackRepository.deleteAll(existing.values());
            changed = true;
        }
        return changed;
    }

    private boolean upsertPhysicalAlbum(AlbumEntity album, ArtistEntity artist, String source, PhysicalAlbumUpsert u,
                                        OffsetDateTime now) {
        PhysicalAlbumEntity physicalAlbum = physicalAlbumRepository.findBySourceAndExternalId(source, u.externalId())
                .orElseGet(() -> new PhysicalAlbumEntity(source, u.externalId()));
        boolean created = physicalAlbum.getId() == null;
        boolean changed = physicalAlbum.apply(album.getId(), u.format(), u.releasedAt(), u.name().strip(),
                keep(u.thumbnailUrl(), keep(physicalAlbum.getThumbnailUrl(), album.getThumbnailUrl())), u.sourceHash(), now);
        changed |= fillDetail(physicalAlbum, u.description(), now);
        physicalAlbum = physicalAlbumRepository.save(physicalAlbum);
        ProductService.OptionsResult options = productService.upsertOptions(physicalAlbum.getId(), u.format(), inputs(u.options()));
        // 새 상품은 발매일이 최근이거나 모를 때만, 이미 있던 상품에 옵션(컬러반 등)이 새로 생기면 그 옵션만 실어 보냅니다
        boolean announce = created ? u.releasedAt() == null || recent(u.releasedAt()) : !options.created().isEmpty();
        if (announce) events.physicalAlbumReleased(physicalAlbum, album, artist, options.created());
        return created || changed || options.changed();
    }

    /** 상세 설명 - 저장된 값이 없을 때만 채움 (관리자가 쓴 값, 지운 뒤 다시 쓴 값은 수집으로 바뀌지 않음) */
    private static boolean fillDetail(ProductEntity product, String description, OffsetDateTime now) {
        return product.getDescription() == null && description != null && !description.isBlank()
                && product.applyDescription(description, now);
    }

    /** 미래 발매일(예약 판매)도 최근으로 봅니다. 발매일을 모르는 앨범은 알리지 않습니다 */
    private boolean recent(LocalDate releasedAt) {
        return releasedAt != null && !releasedAt.isBefore(LocalDate.now(clock).minusDays(releaseWindowDays));
    }

    private static List<ProductService.OptionInput> inputs(List<OptionUpsert> options) {
        return options.stream()
                .map(o -> new ProductService.OptionInput(null, o.name().strip(), o.vinylColor(), o.size(), o.price(), o.qty()))
                .toList();
    }

    /** 배열 값 - null 은 빈 배열로, 같은 값이 두 번 오면 하나로 (순서는 유지, 첫 값이 대표) */
    private static <E> List<E> distinct(List<E> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    /** 관리자가 넣는 값 - null 이면 저장된 값 그대로 (새 행이면 저장된 값도 null) */
    private static <T> T keep(T incoming, T stored) {
        return incoming != null ? incoming : stored;
    }

    /** 트랙 태그 - null 이면 그대로, 빈 배열이면 지움, 값이 있으면 중복을 빼고 그 값으로 */
    private static <E> List<E> tags(List<E> incoming, List<E> stored) {
        return incoming == null ? (stored == null ? List.of() : stored) : distinct(incoming);
    }

    private static boolean sameHash(String incoming, String stored) {
        return incoming != null && Objects.equals(incoming, stored);
    }
}
