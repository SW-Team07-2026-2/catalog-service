package com.indie.catalog.domain.album.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AlbumDetail(long id, long artistId, String artistName, String title, String type, LocalDate releasedAt,
                          String thumbnailUrl, List<TrackItem> tracks, List<LpItem> lps, SaleInfo cd) {}
