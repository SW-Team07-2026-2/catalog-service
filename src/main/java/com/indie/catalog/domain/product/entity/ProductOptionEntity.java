package com.indie.catalog.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * product_option — 판매 단위. 가격이 여기 있고 재고(stock) · 장바구니 · 주문이 이 id 를 가리킵니다.
 * (product_id, name) 이 수집 갱신 기준 — 이름이 바뀌면 새 옵션이고, 예전 옵션은 주문 · 보유 음반이 가리켜 남습니다.
 * vinyl_color 는 LP 옵션에만, size 는 굿즈 옵션에만 씁니다 (OptionRule 이 적재 때 검사).
 */
@Entity
@Table(name = "product_option",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_option_name", columnNames = {"product_id", "name"}))
public class ProductOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private String name;
    @Enumerated(EnumType.STRING)
    private VinylColor vinylColor;
    @Enumerated(EnumType.STRING)
    private OptionSize size;
    private Integer price;
    private OffsetDateTime createdAt;

    protected ProductOptionEntity() {}

    public ProductOptionEntity(Long productId, String name, OffsetDateTime now) {
        this.productId = productId;
        this.name = name;
        this.createdAt = now;
    }

    /** 값을 덮어쓰고 바뀐 게 있으면 true */
    public boolean apply(VinylColor vinylColor, OptionSize size, Integer price) {
        boolean changed = this.vinylColor != vinylColor || this.size != size || !Objects.equals(this.price, price);
        this.vinylColor = vinylColor;
        this.size = size;
        this.price = price;
        return changed;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public VinylColor getVinylColor() { return vinylColor; }
    public OptionSize getSize() { return size; }
    public Integer getPrice() { return price; }
}
