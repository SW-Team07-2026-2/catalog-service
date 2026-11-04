package com.indie.catalog.domain.artist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.Objects;

/** artist — (source, external_id) 가 수집 멱등 upsert 기준입니다 */
@Entity
@Table(name = "artist", uniqueConstraints = @UniqueConstraint(name = "uq_artist_source", columnNames = {"source", "external_id"}))
public class ArtistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String genre;

    private Long labelId;

    private Integer debutYear;

    private String profileUrl;

    private String source;

    private String externalId;

    private String sourceHash;

    private OffsetDateTime updatedAt;

    protected ArtistEntity() {}

    public ArtistEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    /** 값을 덮어쓰고 바뀐 게 있으면 true — artist.updated 발행 판단 */
    public boolean apply(String name, String genre, Long labelId, Integer debutYear, String profileUrl,
                         String sourceHash, OffsetDateTime now) {
        boolean changed = !Objects.equals(this.name, name) || !Objects.equals(this.genre, genre)
                || !Objects.equals(this.labelId, labelId) || !Objects.equals(this.debutYear, debutYear)
                || !Objects.equals(this.profileUrl, profileUrl);
        this.name = name;
        this.genre = genre;
        this.labelId = labelId;
        this.debutYear = debutYear;
        this.profileUrl = profileUrl;
        this.sourceHash = sourceHash;
        if (changed || this.updatedAt == null) this.updatedAt = now;
        return changed;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGenre() { return genre; }
    public Long getLabelId() { return labelId; }
    public Integer getDebutYear() { return debutYear; }
    public String getProfileUrl() { return profileUrl; }
    public String getExternalId() { return externalId; }
    public String getSourceHash() { return sourceHash; }
}
