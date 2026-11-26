package com.indie.catalog.domain.product.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

/** goods — 아티스트 굿즈 (아티스트에 바로 매핑). 색상은 상품 이름에 넣고 선택지는 사이즈(product_option)만 둡니다 */
@Entity
@Table(name = "goods")
@DiscriminatorValue("GOODS")
@PrimaryKeyJoinColumn(name = "id")
public class GoodsEntity extends ProductEntity {

    private Long artistId;
    @Enumerated(EnumType.STRING)
    private GoodsCategory category;
    private String description;

    protected GoodsEntity() {}

    public GoodsEntity(String source, String externalId) {
        super(source, externalId);
    }

    @Override
    public Type getProductType() { return Type.GOODS; }

    public boolean apply(Long artistId, GoodsCategory category, String name, String thumbnailUrl, String sourceHash,
                         OffsetDateTime now) {
        boolean changed = !Objects.equals(this.artistId, artistId) || this.category != category;
        this.artistId = artistId;
        this.category = category;
        return applyCommon(name, thumbnailUrl, sourceHash, changed, now);
    }

    /** 11/26 상세 설명 — null 은 그대로 · 빈 글은 지움. 바뀌었으면 true (관리자만 씁니다) */
    public boolean applyDescription(String description, OffsetDateTime now) {
        if (description == null) return false;
        String next = description.isBlank() ? null : description.strip();
        if (Objects.equals(this.description, next)) return false;
        this.description = next;
        return applyCommon(getName(), getThumbnailUrl(), getSourceHash(), true, now);
    }

    public Long getArtistId() { return artistId; }
    public GoodsCategory getCategory() { return category; }
    public String getDescription() { return description; }
}
