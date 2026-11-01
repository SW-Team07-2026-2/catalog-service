package com.indie.catalog.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

/** goods — 아티스트 굿즈. 판매 정보는 product(GOODS, goods.id) */
@Entity
@Table(name = "goods", uniqueConstraints = @UniqueConstraint(name = "uq_goods_source", columnNames = {"source", "external_id"}))
public class GoodsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long artistId;

    private String name;

    private String category;

    private String source;

    private String externalId;

    private String sourceHash;

    protected GoodsEntity() {}

    public GoodsEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public boolean apply(Long artistId, String name, String category, String sourceHash) {
        boolean changed = !Objects.equals(this.artistId, artistId) || !Objects.equals(this.name, name)
                || !Objects.equals(this.category, category);
        this.artistId = artistId;
        this.name = name;
        this.category = category;
        this.sourceHash = sourceHash;
        return changed;
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
}
