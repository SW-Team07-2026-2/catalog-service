package com.indie.catalog.domain.ingest.dto.event;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.global.event.EventPublisher;
import com.indie.catalog.domain.product.entity.ProductEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog 가 발행하는 이벤트 4종 (08 §11) — 모두 업무 데이터와 같은 트랜잭션에서 (EventPublisher = outbox).
 * <pre>
 * artist.updated   아티스트 생성 · 변경          → Concert · AI
 * album.released   앨범 신규 등록                → Member(피드) · AI
 * album.updated    앨범 · LP · 판매정보 변경       → AI
 * stock.restocked  입고로 재고 0 → N             → Member(피드)
 * </pre>
 * 🔴 전부 내부 upsert(입고 · 수집)에서만 나갑니다. 주문 차감 · 보상 복구로 재고가 바뀌는 건 이벤트가 아닙니다 —
 * 주문 1건마다 album.updated, 결제 실패마다 재입고 알림이 나가면 팔로워 배지가 스팸이 됩니다.
 */
@Component
public class CatalogEvents {

    private final EventPublisher publisher;

    public CatalogEvents(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void artistUpdated(ArtistEntity a) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("artistId", a.getId());
        p.put("name", a.getName());
        p.put("genre", a.getGenre());
        p.put("profileUrl", a.getProfileUrl());
        publisher.publish("artist.updated", p);
    }

    public void albumReleased(AlbumEntity album, String artistName) {
        publisher.publish("album.released", albumSnapshot(album, artistName));
    }

    public void albumUpdated(AlbumEntity album, String artistName, List<String> changedFields) {
        Map<String, Object> p = albumSnapshot(album, artistName);
        p.put("changedFields", changedFields);
        publisher.publish("album.updated", p);
    }

    /** albumId 는 LP · CD 만 (굿즈는 null) */
    public void stockRestocked(ProductEntity product, Long albumId, long artistId, String artistName, int qty) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("productId", product.getId());
        p.put("productType", product.getProductType().name());
        p.put("albumId", albumId);
        p.put("artistId", artistId);
        p.put("artistName", artistName);
        p.put("title", product.getName());
        p.put("price", product.getPrice());
        p.put("qty", qty);
        p.put("thumbnailUrl", product.getThumbnailUrl());
        publisher.publish("stock.restocked", p);
    }

    private static Map<String, Object> albumSnapshot(AlbumEntity album, String artistName) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("albumId", album.getId());
        p.put("artistId", album.getArtistId());
        p.put("artistName", artistName);
        p.put("title", album.getTitle());
        p.put("type", album.getType().name());
        p.put("releasedAt", album.getReleasedAt() == null ? null : album.getReleasedAt().toString());
        p.put("thumbnailUrl", album.getThumbnailUrl());
        return p;
    }
}
