package com.indie.catalog.domain.album.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "track", uniqueConstraints = @UniqueConstraint(name = "uq_track_no", columnNames = {"album_id", "track_no"}))
public class TrackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long albumId;

    private Integer trackNo;

    private String title;

    protected TrackEntity() {}

    public TrackEntity(Long albumId, Integer trackNo, String title) {
        this.albumId = albumId;
        this.trackNo = trackNo;
        this.title = title;
    }

    public Long getId() { return id; }
    public Long getAlbumId() { return albumId; }
    public Integer getTrackNo() { return trackNo; }
    public String getTitle() { return title; }
}
