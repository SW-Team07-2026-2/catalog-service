package com.indie.catalog.domain.album.dto.response;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;

import java.time.LocalDate;
import java.util.List;

public record AlbumDetail(long id, long artistId, String artistName, String title, AlbumEntity.Type type, List<Genre> genres,
                          LocalDate releasedAt, String thumbnailUrl, List<TrackItem> tracks,
                          List<PhysicalAlbumItem> physicalAlbums) {}
