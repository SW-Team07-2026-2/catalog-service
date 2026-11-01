package com.indie.catalog.domain.ingest.service;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.LpEntity;
import com.indie.catalog.domain.album.entity.TrackEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.album.repository.LpRepository;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.entity.BandMemberEntity;
import com.indie.catalog.domain.artist.entity.LabelEntity;
import com.indie.catalog.domain.artist.exception.ArtistErrorCode;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.artist.repository.BandMemberRepository;
import com.indie.catalog.domain.artist.repository.LabelRepository;
import com.indie.catalog.domain.ingest.dto.event.CatalogEvents;
import com.indie.catalog.domain.ingest.dto.request.AlbumUpsert;
import com.indie.catalog.domain.ingest.dto.request.ArtistUpsert;
import com.indie.catalog.domain.ingest.dto.request.DiffItem;
import com.indie.catalog.domain.ingest.dto.request.DiffRequest;
import com.indie.catalog.domain.ingest.dto.request.GoodsUpsert;
import com.indie.catalog.domain.ingest.dto.request.LpUpsert;
import com.indie.catalog.domain.ingest.dto.request.MemberUpsert;
import com.indie.catalog.domain.ingest.dto.request.SaleUpsert;
import com.indie.catalog.domain.ingest.dto.request.TrackUpsert;
import com.indie.catalog.domain.ingest.dto.response.DiffResult;
import com.indie.catalog.domain.ingest.dto.response.ItemResult;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.repository.GoodsRepository;
import com.indie.catalog.domain.product.service.ProductService;
import com.indie.catalog.domain.search.repository.SearchIndex;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import com.indie.catalog.global.common.util.SourceHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 수집 · 시드 적재 (I4 · I5 · I15). 남의 스키마에 직접 쓰지 않고 이 API 를 거치므로 이벤트 발행도 여기서 함께 됩니다 (02 ⑤).
 * <ul>
 *   <li>(source, external_id) 기준 멱등 upsert — 같은 요청을 다시 보내도 결과가 같습니다</li>
 *   <li>항목마다 트랜잭션 — 한 건이 실패해도 나머지는 적재되고, 결과에 FAILED 로 표시합니다</li>
 *   <li>이벤트는 실제로 값이 바뀐 경우에만 — 같은 값을 다시 넣으면 아무것도 나가지 않습니다</li>
 * </ul>
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ArtistRepository artistRepository;
    private final LabelRepository labelRepository;
    private final BandMemberRepository bandMemberRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final LpRepository lpRepository;
    private final GoodsRepository goodsRepository;
    private final ProductService productService;
    private final SearchIndex searchIndex;
    private final CatalogEvents events;
    private final ArtistCache artistCache;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IngestService(ArtistRepository artistRepository, LabelRepository labelRepository,
                         BandMemberRepository bandMemberRepository, AlbumRepository albumRepository,
                         TrackRepository trackRepository, LpRepository lpRepository, GoodsRepository goodsRepository,
                         ProductService productService, SearchIndex searchIndex, CatalogEvents events,
                         ArtistCache artistCache, TransactionTemplate tx, Clock clock) {
        this.artistRepository = artistRepository;
        this.labelRepository = labelRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.lpRepository = lpRepository;
        this.goodsRepository = goodsRepository;
        this.productService = productService;
        this.searchIndex = searchIndex;
        this.events = events;
        this.artistCache = artistCache;
        this.tx = tx;
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
            case LP -> lpRepository.findHashes(request.source(), ids);
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
        boolean changed = artist.apply(u.name().strip(), u.genre(), labelId, u.debutYear(), u.profileUrl(), u.sourceHash(), now);
        artist = artistRepository.save(artist);
        if (u.members() != null) changed |= replaceMembers(artist.getId(), u.members());

        if (created || changed) {
            searchIndex.put(SearchIndex.TargetType.ARTIST, artist.getId(), artist.getName());
            events.artistUpdated(artist);
        }
        boolean goodsChanged = false;
        if (u.goods() != null) {
            for (GoodsUpsert g : u.goods()) goodsChanged |= upsertGoods(artist, u.source(), g);
        }
        if (created || changed || goodsChanged) artistCache.evictAfterCommit(artist.getId());
        return new ItemResult(u.externalId(), artist.getId(), created ? ItemResult.Result.CREATED
                : changed || goodsChanged ? ItemResult.Result.UPDATED : ItemResult.Result.UNCHANGED, null);
    }

    private boolean replaceMembers(long artistId, List<MemberUpsert> members) {
        List<String> before = bandMemberRepository.findByArtistIdOrderById(artistId).stream()
                .map(m -> m.getName() + "|" + m.getRole()).toList();
        List<String> after = members.stream().map(m -> m.name().strip() + "|" + m.role()).toList();
        if (before.equals(after)) return false;
        bandMemberRepository.deleteByArtistId(artistId);
        members.forEach(m -> bandMemberRepository.save(new BandMemberEntity(artistId, m.name().strip(), m.role())));
        return true;
    }

    private boolean upsertGoods(ArtistEntity artist, String source, GoodsUpsert g) {
        GoodsEntity goods = goodsRepository.findBySourceAndExternalId(source, g.externalId())
                .orElseGet(() -> new GoodsEntity(source, g.externalId()));
        boolean created = goods.getId() == null;
        boolean changed = goods.apply(artist.getId(), g.name().strip(), g.category(), g.sourceHash());
        goods = goodsRepository.save(goods);
        if (g.sale() != null) {
            ProductService.SaleResult sale = productService.upsertSale(ProductEntity.Type.GOODS, goods.getId(),
                    goods.getName(), g.thumbnailUrl(), sale(g.sale()));
            changed |= sale.changed();
            if (sale.restocked()) events.stockRestocked(sale.product(), null, artist.getId(), artist.getName(), sale.qty());
        }
        return created || changed;
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

        List<String> changed = album.apply(artist.getId(), u.title().strip(), u.type(), u.releasedAt(), u.thumbnailUrl(),
                u.sourceHash(), now);
        album = albumRepository.save(album);
        if (created || changed.contains("title")) searchIndex.put(SearchIndex.TargetType.ALBUM, album.getId(), album.getTitle());
        if (u.tracks() != null && replaceTracks(album.getId(), u.tracks())) changed.add("tracks");

        if (u.cd() != null) {
            ProductService.SaleResult sale = productService.upsertSale(ProductEntity.Type.CD, album.getId(),
                    album.getTitle() + " (CD)", album.getThumbnailUrl(), sale(u.cd()));
            if (sale.changed()) changed.add("cd");
            if (sale.restocked()) events.stockRestocked(sale.product(), album.getId(), artist.getId(), artist.getName(), sale.qty());
        }
        if (u.lps() != null) {
            boolean lpsChanged = false;
            for (LpUpsert lp : u.lps()) lpsChanged |= upsertLp(album, artist, u.source(), lp);
            if (lpsChanged) changed.add("lps");
        }

        if (created) {
            events.albumReleased(album, artist.getName());
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

    /** 트랙은 통째로 교체 — 번호가 바뀌는 재발매 · 수정에 맞춰 부분 갱신하지 않습니다 */
    private boolean replaceTracks(long albumId, List<TrackUpsert> tracks) {
        List<String> before = trackRepository.findByAlbumIdOrderByTrackNo(albumId).stream()
                .map(t -> t.getTrackNo() + "|" + t.getTitle()).toList();
        List<TrackUpsert> sorted = tracks.stream().sorted((a, b) -> Integer.compare(a.trackNo(), b.trackNo())).toList();
        List<String> after = sorted.stream().map(t -> t.trackNo() + "|" + t.title().strip()).toList();
        if (before.equals(after)) return false;
        if (after.stream().map(s -> s.substring(0, s.indexOf('|'))).distinct().count() != after.size()) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "track_no 가 중복됩니다");
        }
        searchIndex.removeTracksOf(albumId);           // 트랙 행을 지우기 전에 (서브쿼리가 track 을 봄)
        trackRepository.deleteByAlbumId(albumId);
        for (TrackUpsert t : sorted) {
            TrackEntity saved = trackRepository.save(new TrackEntity(albumId, t.trackNo(), t.title().strip()));
            searchIndex.put(SearchIndex.TargetType.TRACK, saved.getId(), saved.getTitle());
        }
        return true;
    }

    private boolean upsertLp(AlbumEntity album, ArtistEntity artist, String source, LpUpsert u) {
        LpEntity lp = lpRepository.findBySourceAndExternalId(source, u.externalId()).orElseGet(() -> new LpEntity(source, u.externalId()));
        boolean created = lp.getId() == null;
        boolean changed = lp.apply(album.getId(), u.pressType(), u.releasedAt(), u.sourceHash());
        lp = lpRepository.save(lp);
        if (u.sale() != null) {
            String name = album.getTitle() + " LP" + (u.pressType() == null ? "" : " (" + u.pressType() + ")");
            ProductService.SaleResult sale = productService.upsertSale(ProductEntity.Type.LP, lp.getId(), name,
                    album.getThumbnailUrl(), sale(u.sale()));
            changed |= sale.changed();
            if (sale.restocked()) events.stockRestocked(sale.product(), album.getId(), artist.getId(), artist.getName(), sale.qty());
        }
        return created || changed;
    }

    private static boolean sameHash(String incoming, String stored) {
        return incoming != null && Objects.equals(incoming, stored);
    }

    private static ProductService.Sale sale(SaleUpsert s) {
        return new ProductService.Sale(s.price(), s.qty(), s.purchaseUrl());
    }
}
