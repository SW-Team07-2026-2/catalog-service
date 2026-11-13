package com.indie.catalog.domain.product.entity;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * product — 상품 부모. physical_album · goods 가 상속하고 id 를 공유합니다 (JOINED).
 * 가격 · 재고는 여기에 없습니다 — 판매 단위는 product_option 이고, 주문 · 장바구니는 옵션을 가리킵니다.
 * purchase_url 은 실제 판매처를 크롤링해 온 링크 1개입니다.
 */
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uq_product_source", columnNames = {"source", "external_id"}))
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "product_type", length = 16)
public abstract class ProductEntity {

    public enum Type { PHYSICAL_ALBUM, GOODS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String thumbnailUrl;
    private String purchaseUrl;
    private String source;
    private String externalId;
    private String sourceHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected ProductEntity() {}

    protected ProductEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public abstract Type getProductType();

    /** 공통 값을 덮어쓰고 바뀐 게 있으면 true. 자식의 apply 가 자기 값과 함께 부릅니다 */
    protected boolean applyCommon(String name, String thumbnailUrl, String purchaseUrl, String sourceHash,
                                  boolean childChanged, OffsetDateTime now) {
        boolean changed = childChanged || !Objects.equals(this.name, name) || !Objects.equals(this.thumbnailUrl, thumbnailUrl)
                || !Objects.equals(this.purchaseUrl, purchaseUrl);
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.purchaseUrl = purchaseUrl;
        this.sourceHash = sourceHash;
        if (this.createdAt == null) this.createdAt = now;
        if (changed || this.updatedAt == null) this.updatedAt = now;
        return changed;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getPurchaseUrl() { return purchaseUrl; }
    public String getSourceHash() { return sourceHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
