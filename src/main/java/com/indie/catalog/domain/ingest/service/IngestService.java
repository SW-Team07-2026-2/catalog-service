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
 * 수집 · 시드 적재 (I4 · I5 · I15). 남의 스키마에 직접 쓰지 않고 이 API 를 거치므로 이벤트 발행도 여기서 함께 됩니다 (02 ⑤).
 * <ul>
 *   <li>(source, external_id) 기준 멱등 upsert — 같은 요청을 다시 보내도 결과가 같습니다</li>
 *   <li>항목마다 트랜잭션 — 한 건이 실패해도 나머지는 적재되고, 결과에 FAILED 로 표시합니다</li>
 *   <li>이벤트는 실제로 값이 바뀐 경우에만 — 같은 값을 다시 넣으면 아무것도 나가지 않습니다</li>
 *   <li>발매 알림 이벤트는 새로 생긴 것만 · 음반은 발매일이 최근(app.event.release-window-days)일 때만</li>
 * </ul>
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

    public List<ItemResult> upsertArtists(List<ArtistUpsert> items) {
        return items.stream().map(item -> eachInTx(item.externalId(), () -> upsertArtist(item))).toList();
    }

    public List<ItemResult> upsertAlbums(List<AlbumUpsert> items) {
        return items.stream().map(item -> eachInTx(item.externalId(), () -> upsertAlbum(item))).toList();
    }

    /** I15 — 수집기는 "뭐가 새로운지"만 묻고, 판정 기준(해시 비교)은 여기 한 곳에만 둡니다 */
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
            // 같은 external_id 를 두 요청이 동시에 처음 넣은 경우 — 다시 보내면 UPDATE 경로로 성공합니다
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
        boolean changed = artist.apply(u.name().strip(), distinct(u.genres()), labelId, u.debutYear(), u.profileUrl(),
                u.sourceHash(), now);
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
        boolean changed = goods.apply(artist.getId(), g.category(), g.name().strip(), g.thumbnailUrl(), g.purchaseUrl(),
                g.sourceHash(), now);
        goods = goodsRepository.save(goods);
        ProductService.OptionsResult options = productService.upsertOptions(goods.getId(), null, inputs(g.options()));
        if (created) {
            events.goodsReleased(goods, artist, g.options().stream().mapToInt(OptionUpsert::price).min().orElseThrow());
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
                u.thumbnailUrl(), u.sourceHash(), now);
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
     * 트랙을 track_no 기준으로 맞춥니다 — 같은 번호는 행을 유지한 채 값만 바꾸고, 요청에 없는 번호는 지우고, 새 번호는 넣습니다.
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
            changed |= track.apply(t.title().strip(), distinct(t.genres()), t.tempo(), distinct(t.moods()),
                    distinct(t.emotions()), distinct(t.styles())) || isNew;
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
                u.thumbnailUrl() == null ? album.getThumbnailUrl() : u.thumbnailUrl(), u.purchaseUrl(), u.sourceHash(), now);
        physicalAlbum = physicalAlbumRepository.save(physicalAlbum);
        ProductService.OptionsResult options = productService.upsertOptions(physicalAlbum.getId(), u.format(), inputs(u.options()));
        // 새 상품은 발매일이 최근이거나 모를 때만 · 이미 있던 상품에 옵션(컬러반 등)이 새로 생기면 그 옵션만 실어 보냅니다
        boolean announce = created ? u.releasedAt() == null || recent(u.releasedAt()) : !options.created().isEmpty();
        if (announce) events.physicalAlbumReleased(physicalAlbum, album, artist, options.created());
        return created || changed || options.changed();
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

    /** 배열 값 — null 은 빈 배열로, 같은 값이 두 번 오면 하나로 (순서는 유지 · 첫 값이 대표) */
    private static <E> List<E> distinct(List<E> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private static boolean sameHash(String incoming, String stored) {
        return incoming != null && Objects.equals(incoming, stored);
    }
}
