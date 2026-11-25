package com.indie.catalog.domain.ingest.service;

import com.indie.catalog.domain.album.dto.response.AlbumDetail;
import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.album.entity.TrackEntity;
import com.indie.catalog.domain.album.repository.AlbumRepository;
import com.indie.catalog.domain.album.repository.TrackRepository;
import com.indie.catalog.domain.album.service.AlbumService;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.artist.repository.ArtistCache;
import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.artist.service.ArtistService;
import com.indie.catalog.domain.ingest.dto.event.CatalogEvents;
import com.indie.catalog.domain.ingest.dto.request.AdminAlbumUpdate;
import com.indie.catalog.domain.ingest.dto.request.AdminAlbumUpdate.TrackTags;
import com.indie.catalog.domain.ingest.dto.request.AdminArtistUpdate;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자(Studio)가 이미 들어간 아티스트 · 앨범에 넣는 값 — 이미지 · 트랙 태그 (11/18 결정: 크롤러는 비워 두고 관리자가 넣음) · 아티스트 성별 · 구성 (11/22).
 * 수집 적재와 같은 엔티티 apply · 같은 이벤트(artist.updated · album.updated) · 같은 캐시 삭제를 씁니다.
 * 수집 출처의 해시(source_hash)는 그대로 둡니다 — 다음 수집이 이 값을 지우지 않는 건 적재의 "null 이면 그대로" 규칙 덕분입니다.
 * 응답은 커밋 뒤에 다시 읽습니다 (캐시를 커밋 뒤에 지우므로).
 */
@Service
public class AdminEditService {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final CatalogEvents events;
    private final ArtistCache artistCache;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AdminEditService(ArtistRepository artistRepository, AlbumRepository albumRepository, TrackRepository trackRepository,
                            ArtistService artistService, AlbumService albumService, CatalogEvents events, ArtistCache artistCache,
                            TransactionTemplate tx, Clock clock) {
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.artistService = artistService;
        this.albumService = albumService;
        this.events = events;
        this.artistCache = artistCache;
        this.tx = tx;
        this.clock = clock;
    }

    public ArtistDetail updateArtist(long id, AdminArtistUpdate r) {
        tx.executeWithoutResult(s -> {
            ArtistEntity a = artistRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
            String profileUrl = r.profileUrl() != null ? r.profileUrl() : a.getProfileUrl();
            OffsetDateTime now = OffsetDateTime.now(clock);
            boolean changed = a.apply(a.getName(), a.getGenres(), a.getLabelId(), a.getDebutYear(), profileUrl, r.gender(), r.composition(),   // 성별 · 구성 null = 그대로
                    a.getSourceHash(), now);
            changed |= a.applyBio(r.bio(), now);                    // 11/25 소개 글 null = 그대로
            if (changed) {
                artistRepository.save(a);
                events.artistUpdated(a);
                artistCache.evictAfterCommit(id);
            }
        });
        return artistService.detail(id);
    }

    public AlbumDetail updateAlbum(long id, AdminAlbumUpdate r) {
        tx.executeWithoutResult(s -> {
            AlbumEntity album = albumRepository.findById(id).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
            List<String> changed = new ArrayList<>();
            if (r.thumbnailUrl() != null) {
                changed.addAll(album.apply(album.getArtistId(), album.getTitle(), album.getType(), album.getGenres(),
                        album.getReleasedAt(), r.thumbnailUrl(), album.getSourceHash(), OffsetDateTime.now(clock)));
                albumRepository.save(album);
            }
            if (album.applyDescription(r.description(), OffsetDateTime.now(clock))) {   // 11/25 소개 글 null = 그대로
                albumRepository.save(album);
                changed.add("description");
            }
            if (r.tracks() != null && !r.tracks().isEmpty() && tags(id, r.tracks())) changed.add("tracks");
            if (!changed.isEmpty()) {
                String artistName = artistRepository.findById(album.getArtistId()).map(ArtistEntity::getName).orElse(null);
                events.albumUpdated(album, artistName, changed);
                artistCache.evictAfterCommit(album.getArtistId());
            }
        });
        return albumService.detail(id);
    }

    private boolean tags(long albumId, List<TrackTags> tracks) {
        Map<Integer, TrackEntity> byNo = trackRepository.findByAlbumIdOrderByTrackNo(albumId).stream()
                .collect(Collectors.toMap(TrackEntity::getTrackNo, Function.identity()));
        boolean changed = false;
        for (TrackTags t : tracks) {
            TrackEntity track = byNo.get(t.trackNo());
            if (track == null) throw new GeneralException(CommonErrorCode.INVALID_REQUEST, "track_no " + t.trackNo() + " 가 이 앨범에 없습니다");
            if (track.apply(track.getTitle(), pick(t.genres(), track.getGenres()), t.tempo() == null ? track.getTempo() : t.tempo(),
                    pick(t.moods(), track.getMoods()), pick(t.emotions(), track.getEmotions()), pick(t.styles(), track.getStyles()))) {
                trackRepository.save(track);
                changed = true;
            }
        }
        return changed;
    }

    private static <E> List<E> pick(List<E> incoming, List<E> stored) {
        return incoming == null ? stored : incoming.stream().distinct().toList();
    }
}
