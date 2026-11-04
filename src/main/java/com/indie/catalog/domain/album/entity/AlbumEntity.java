package com.indie.catalog.domain.album.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "album", uniqueConstraints = @UniqueConstraint(name = "uq_album_source", columnNames = {"source", "external_id"}))
public class AlbumEntity {

    public enum Type { ALBUM, EP, SINGLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long artistId;

    private String title;

    @Enumerated(EnumType.STRING)
    private Type type;

    private LocalDate releasedAt;

    private String thumbnailUrl;

    private String source;

    private String externalId;

    private String sourceHash;

    private OffsetDateTime updatedAt;

    protected AlbumEntity() {}

    public AlbumEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    /** 값을 덮어쓰고 바뀐 필드 이름을 돌려줍니다 — album.updated 의 changedFields */
    public List<String> apply(Long artistId, String title, Type type, LocalDate releasedAt, String thumbnailUrl,
                              String sourceHash, OffsetDateTime now) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(this.artistId, artistId)) changed.add("artistId");
        if (!Objects.equals(this.title, title)) changed.add("title");
        if (this.type != type) changed.add("type");
        if (!Objects.equals(this.releasedAt, releasedAt)) changed.add("releasedAt");
        if (!Objects.equals(this.thumbnailUrl, thumbnailUrl)) changed.add("thumbnailUrl");
        this.artistId = artistId;
        this.title = title;
        this.type = type;
        this.releasedAt = releasedAt;
        this.thumbnailUrl = thumbnailUrl;
        this.sourceHash = sourceHash;
        if (!changed.isEmpty() || this.updatedAt == null) this.updatedAt = now;
        return changed;
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public String getTitle() { return title; }
    public Type getType() { return type; }
    public LocalDate getReleasedAt() { return releasedAt; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getSourceHash() { return sourceHash; }
}
