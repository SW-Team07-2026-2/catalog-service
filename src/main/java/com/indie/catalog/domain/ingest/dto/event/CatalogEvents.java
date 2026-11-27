package com.indie.catalog.domain.ingest.dto.event;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.artist.entity.ArtistEntity;
import com.indie.catalog.domain.product.entity.GoodsEntity;
import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import com.indie.catalog.domain.product.entity.ProductOptionEntity;
import com.indie.catalog.global.event.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog 변경 -> Outbox -> Kafka -> Concert, Member, AI 연동 이벤트.
 * 업무 데이터와 같은 트랜잭션에서 저장합니다. 주문 이벤트는 OrderEvents에서 정의합니다.
 */
@Component
public class CatalogEvents {

    private final EventPublisher publisher;

    public CatalogEvents(EventPublisher publisher) {
        this.publisher = publisher;
    }

    /** genre 는 대표 장르 1개(genres 의 첫 값, 없으면 null) - Concert 가 복제해 두는 값이라 남깁니다 */
    public void artistUpdated(ArtistEntity a) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("artistId", a.getId());
        p.put("name", a.getName());
        p.put("genre", a.getGenres().isEmpty() ? null : a.getGenres().get(0).name());
        p.put("genres", names(a.getGenres()));
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

    /** options - 이번에 새로 생긴 옵션만 (같은 수집에서 여러 개가 생기면 1건으로 묶음) */
    public void physicalAlbumReleased(PhysicalAlbumEntity physicalAlbum, AlbumEntity album, ArtistEntity artist,
                                      List<ProductOptionEntity> newOptions) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("physicalAlbumId", physicalAlbum.getId());
        p.put("albumId", album.getId());
        p.put("albumTitle", album.getTitle());
        p.put("artistId", artist.getId());
        p.put("artistName", artist.getName());
        p.put("format", physicalAlbum.getFormat().name());
        p.put("name", physicalAlbum.getName());
        p.put("thumbnailUrl", physicalAlbum.getThumbnailUrl());
        p.put("options", newOptions.stream().map(o -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("optionId", o.getId());
            option.put("name", o.getName());
            option.put("vinylColor", o.getVinylColor() == null ? null : o.getVinylColor().name());
            option.put("price", o.getPrice());
            return option;
        }).toList());
        publisher.publish("physical_album.released", p);
    }

    /** price - 옵션 중 가장 낮은 가격 */
    public void goodsReleased(GoodsEntity goods, ArtistEntity artist, int price) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("goodsId", goods.getId());
        p.put("artistId", artist.getId());
        p.put("artistName", artist.getName());
        p.put("name", goods.getName());
        p.put("category", goods.getCategory().name());
        p.put("price", price);
        p.put("thumbnailUrl", goods.getThumbnailUrl());
        publisher.publish("goods.released", p);
    }

    private static Map<String, Object> albumSnapshot(AlbumEntity album, String artistName) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("albumId", album.getId());
        p.put("artistId", album.getArtistId());
        p.put("artistName", artistName);
        p.put("title", album.getTitle());
        p.put("type", album.getType().name());
        p.put("genres", names(album.getGenres()));
        p.put("releasedAt", album.getReleasedAt() == null ? null : album.getReleasedAt().toString());
        p.put("thumbnailUrl", album.getThumbnailUrl());
        return p;
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }
}
