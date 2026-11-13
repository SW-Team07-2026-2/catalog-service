package com.indie.catalog.domain.product.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * physical_album — 앨범의 실물 음반 (LP · CD · 카세트). 한 앨범에 같은 매체가 여러 개일 수 있습니다
 * (초판 · 재판 · N차 — 상품 이름으로 구분). 판 색 같은 선택지는 product_option 입니다.
 */
@Entity
@Table(name = "physical_album")
@DiscriminatorValue("PHYSICAL_ALBUM")
@PrimaryKeyJoinColumn(name = "id")
public class PhysicalAlbumEntity extends ProductEntity {

    private Long albumId;
    @Enumerated(EnumType.STRING)
    private PhysicalFormat format;
    private LocalDate releasedAt;

    protected PhysicalAlbumEntity() {}

    public PhysicalAlbumEntity(String source, String externalId) {
        super(source, externalId);
    }

    @Override
    public Type getProductType() { return Type.PHYSICAL_ALBUM; }

    public boolean apply(Long albumId, PhysicalFormat format, LocalDate releasedAt, String name, String thumbnailUrl,
                         String purchaseUrl, String sourceHash, OffsetDateTime now) {
        boolean changed = !Objects.equals(this.albumId, albumId) || this.format != format
                || !Objects.equals(this.releasedAt, releasedAt);
        this.albumId = albumId;
        this.format = format;
        this.releasedAt = releasedAt;
        return applyCommon(name, thumbnailUrl, purchaseUrl, sourceHash, changed, now);
    }

    public Long getAlbumId() { return albumId; }
    public PhysicalFormat getFormat() { return format; }
    public LocalDate getReleasedAt() { return releasedAt; }
}
