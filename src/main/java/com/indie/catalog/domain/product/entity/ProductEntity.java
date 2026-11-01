package com.indie.catalog.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

/**
 * product — 판매 단위. LP · CD · GOODS 를 하나로 묶습니다 (위시리스트 · 주문이 가리키는 대상).
 * 우리가 주 판매처이고, purchase_url 은 실제 판매처를 크롤링해 온 구매처 링크 1개입니다 (재고 · 가격 비교는 하지 않음).
 */
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uq_product_ref", columnNames = {"product_type", "ref_id"}))
public class ProductEntity {

    public enum Type { LP, CD, GOODS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Type productType;

    private Long refId;                 // lp.id | album.id | goods.id

    private String name;

    private Integer price;

    private String thumbnailUrl;

    private String purchaseUrl;

    protected ProductEntity() {}

    public ProductEntity(Type productType, Long refId) {
        this.productType = productType;
        this.refId = refId;
    }

    /** 가격 · 이름 · 링크를 덮어쓰고 바뀐 게 있으면 true (재고 수량은 stock 쪽) */
    public boolean apply(String name, Integer price, String thumbnailUrl, String purchaseUrl) {
        boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.price, price)
                || !Objects.equals(this.thumbnailUrl, thumbnailUrl) || !Objects.equals(this.purchaseUrl, purchaseUrl);
        this.name = name;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        this.purchaseUrl = purchaseUrl;
        return changed;
    }

    public Long getId() { return id; }
    public Type getProductType() { return productType; }
    public Long getRefId() { return refId; }
    public String getName() { return name; }
    public Integer getPrice() { return price; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getPurchaseUrl() { return purchaseUrl; }
}
