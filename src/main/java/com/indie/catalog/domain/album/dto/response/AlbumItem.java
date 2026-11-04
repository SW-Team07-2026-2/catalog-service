package com.indie.catalog.domain.album.dto.response;

import java.time.LocalDate;

public record AlbumItem(long id, long artistId, String artistName, String title, String type,
                        LocalDate releasedAt, String thumbnailUrl) {}
