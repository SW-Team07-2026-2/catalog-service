package com.indie.catalog.domain.artist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** artist - (source, external_id) 가 수집 멱등 upsert 기준입니다. 소속사는 label_id (여러 아티스트가 한 소속사) */
@Entity
@Table(name = "artist", uniqueConstraints = @UniqueConstraint(name = "uq_artist_source", columnNames = {"source", "external_id"}))
public class ArtistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long labelId;
    private String name;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Genre> genres = List.of();
    private Integer debutYear;
    private String profileUrl;
    @Enumerated(EnumType.STRING)
    private ArtistGender gender;
    @Enumerated(EnumType.STRING)
    private ArtistComposition composition;
    private String bio;
    /** 팔로워 수 사본 - FollowerCountSync 만 씁니다 (엔티티 저장은 이 값을 건드리지 않음) */
    @Column(insertable = false, updatable = false)
    private long followerCount;
    private String source;
    private String externalId;
    private String sourceHash;
    private OffsetDateTime updatedAt;

    protected ArtistEntity() {}

    public ArtistEntity(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    /**
     * 값을 덮어쓰고 바뀐 게 있으면 true - artist.updated 발행 판단.
     * 성별, 구성은 null 이면 저장된 값을 그대로 둡니다 (수집기는 모름, 관리자가 넣은 값 유지)
     */
    public boolean apply(String name, List<Genre> genres, Long labelId, Integer debutYear, String profileUrl,
                         ArtistGender gender, ArtistComposition composition, String sourceHash, OffsetDateTime now) {
        ArtistGender nextGender = gender != null ? gender : this.gender;
        ArtistComposition nextComposition = composition != null ? composition : this.composition;
        boolean changed = !Objects.equals(this.name, name) || !this.genres.equals(genres)
                || !Objects.equals(this.labelId, labelId) || !Objects.equals(this.debutYear, debutYear)
                || !Objects.equals(this.profileUrl, profileUrl) || this.gender != nextGender || this.composition != nextComposition;
        this.name = name;
        this.genres = List.copyOf(genres);
        this.labelId = labelId;
        this.debutYear = debutYear;
        this.profileUrl = profileUrl;
        this.gender = nextGender;
        this.composition = nextComposition;
        this.sourceHash = sourceHash;
        if (changed || this.updatedAt == null) this.updatedAt = now;
        return changed;
    }

    /**
     * 소개 글 - null 은 그대로, 빈 글은 지움. 바뀌었으면 true (수집은 비어 있을 때만 부름 - IngestService)
     */
    public boolean applyBio(String bio, OffsetDateTime now) {
        if (bio == null) return false;
        String next = bio.isBlank() ? null : bio.strip();
        if (Objects.equals(this.bio, next)) return false;
        this.bio = next;
        this.updatedAt = now;
        return true;
    }

    public Long getId() { return id; }
    public Long getLabelId() { return labelId; }
    public String getName() { return name; }
    public List<Genre> getGenres() { return genres; }
    public Integer getDebutYear() { return debutYear; }
    public String getProfileUrl() { return profileUrl; }
    public String getBio() { return bio; }
    public long getFollowerCount() { return followerCount; }
    public String getExternalId() { return externalId; }
    public String getSourceHash() { return sourceHash; }
}
