package com.indie.catalog.domain.artist.dto.response;

import com.indie.catalog.domain.album.dto.response.AlbumItem;
import com.indie.catalog.domain.album.dto.response.LpItem;
import com.indie.catalog.domain.product.dto.response.GoodsItem;

import java.util.List;

public record ArtistDetail(long id, String name, String genre, String label, Integer debutYear, String profileUrl,
                           List<MemberItem> members, List<AlbumItem> albums,
                           List<LpItem> lps, List<GoodsItem> goods) {}
