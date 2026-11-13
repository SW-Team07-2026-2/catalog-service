package com.indie.catalog.domain.album.entity;

import com.indie.catalog.domain.artist.entity.Genre;
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

import java.util.List;
import java.util.Objects;

/**
 * track — (album_id, track_no) 가 수집 갱신 기준입니다. 같은 번호면 행을 유지하고 값만 바꿉니다
 * (트랙 id 를 Member 메트릭이 들고 있어, 지우고 다시 넣으면 끊깁니다).
 */
@Entity
@Table(name = "track", uniqueConstraints = @UniqueConstraint(name = "uq_track_no", columnNames = {"album_id", "track_no"}))
public class TrackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long albumId;
    private Integer trackNo;
    private String title;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Genre> genres = List.of();
    @Enumerated(EnumType.STRING)
    private Tempo tempo;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Mood> moods = List.of();
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Emotion> emotions = List.of();
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Style> styles = List.of();

    protected TrackEntity() {}

    public TrackEntity(Long albumId, Integer trackNo) {
        this.albumId = albumId;
        this.trackNo = trackNo;
    }

    /** 값을 덮어쓰고 바뀐 게 있으면 true */
    public boolean apply(String title, List<Genre> genres, Tempo tempo, List<Mood> moods, List<Emotion> emotions,
                         List<Style> styles) {
        boolean changed = !Objects.equals(this.title, title) || !this.genres.equals(genres) || this.tempo != tempo
                || !this.moods.equals(moods) || !this.emotions.equals(emotions) || !this.styles.equals(styles);
        this.title = title;
        this.genres = List.copyOf(genres);
        this.tempo = tempo;
        this.moods = List.copyOf(moods);
        this.emotions = List.copyOf(emotions);
        this.styles = List.copyOf(styles);
        return changed;
    }

    public Long getId() { return id; }
    public Long getAlbumId() { return albumId; }
    public Integer getTrackNo() { return trackNo; }
    public String getTitle() { return title; }
    public List<Genre> getGenres() { return genres; }
    public Tempo getTempo() { return tempo; }
    public List<Mood> getMoods() { return moods; }
    public List<Emotion> getEmotions() { return emotions; }
    public List<Style> getStyles() { return styles; }
}
