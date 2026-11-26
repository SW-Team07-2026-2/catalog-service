package com.indie.catalog.domain.product.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.BatchSize;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * product — 상품 부모. physical_album · goods 가 상속하고 id 를 공유합니다 (JOINED).
 * 가격 · 재고는 여기에 없습니다 — 판매 단위는 product_option 이고, 주문 · 장바구니는 옵션을 가리킵니다.
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
    private String source;
    private String externalId;
    private String sourceHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String description;                       // 11/26 상세 설명 (굿즈 · 판본 공통)
    @ElementCollection
    @CollectionTable(name = "product_detail_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 100)                            // ?ids= 여러 상품 상세 — 상품마다 한 번씩 읽지 않게
    @Column(name = "url")
    private List<String> detailImageUrls = new ArrayList<>();   // 11/26 상세 이미지 (보여 줄 순서)

    protected ProductEntity() {}

    protected ProductEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public abstract Type getProductType();

    /** 공통 값을 덮어쓰고 바뀐 게 있으면 true. 자식의 apply 가 자기 값과 함께 부릅니다 */
    protected boolean applyCommon(String name, String thumbnailUrl, String sourceHash, boolean childChanged, OffsetDateTime now) {
        boolean changed = childChanged || !Objects.equals(this.name, name) || !Objects.equals(this.thumbnailUrl, thumbnailUrl);
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.sourceHash = sourceHash;
        if (this.createdAt == null) this.createdAt = now;
        if (changed || this.updatedAt == null) this.updatedAt = now;
        return changed;
    }

    /** 11/26 상세 설명 — null 은 그대로 · 빈 글은 지움. 바뀌었으면 true */
    public boolean applyDescription(String description, OffsetDateTime now) {
        if (description == null) return false;
        String next = description.isBlank() ? null : description.strip();
        if (Objects.equals(this.description, next)) return false;
        this.description = next;
        return applyCommon(name, thumbnailUrl, sourceHash, true, now);
    }

    /** 11/26 상세 이미지 — null 은 그대로 · [] 는 지움 · 같은 주소는 한 번. 바뀌었으면 true */
    public boolean applyDetailImages(List<String> urls, OffsetDateTime now) {
        if (urls == null) return false;
        List<String> next = urls.stream().map(String::strip).distinct().toList();
        if (detailImageUrls.equals(next)) return false;
        detailImageUrls.clear();
        detailImageUrls.addAll(next);
        return applyCommon(name, thumbnailUrl, sourceHash, true, now);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getSourceHash() { return sourceHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
    public List<String> getDetailImageUrls() { return List.copyOf(detailImageUrls); }
}
