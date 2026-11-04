package com.indie.catalog.domain.album.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Objects;

/** lp — 앨범의 LP 발매 (프레싱별 1행). 판매 정보는 product(LP, lp.id) */
@Entity
@Table(name = "lp", uniqueConstraints = @UniqueConstraint(name = "uq_lp_source", columnNames = {"source", "external_id"}))
public class LpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long albumId;

    private String pressType;

    private LocalDate releasedAt;

    private String source;

    private String externalId;

    private String sourceHash;

    protected LpEntity() {}

    public LpEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public boolean apply(Long albumId, String pressType, LocalDate releasedAt, String sourceHash) {
        boolean changed = !Objects.equals(this.albumId, albumId) || !Objects.equals(this.pressType, pressType)
                || !Objects.equals(this.releasedAt, releasedAt);
        this.albumId = albumId;
        this.pressType = pressType;
        this.releasedAt = releasedAt;
        this.sourceHash = sourceHash;
        return changed;
    }

    public Long getId() { return id; }
    public Long getAlbumId() { return albumId; }
    public String getPressType() { return pressType; }
    public LocalDate getReleasedAt() { return releasedAt; }
}
