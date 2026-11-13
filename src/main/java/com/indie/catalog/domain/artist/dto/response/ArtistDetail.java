package com.indie.catalog.domain.artist.dto.response;

import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.artist.entity.Genre;
import com.indie.catalog.domain.product.dto.response.GoodsItem;
import com.indie.catalog.domain.product.dto.response.PhysicalAlbumItem;

import java.util.List;

/** label 은 소속사 이름 (없으면 null) */
public record ArtistDetail(long id, String name, List<Genre> genres, String label, Integer debutYear, String profileUrl,
                           List<AlbumItem> albums, List<PhysicalAlbumItem> physicalAlbums, List<GoodsItem> goods) {}
