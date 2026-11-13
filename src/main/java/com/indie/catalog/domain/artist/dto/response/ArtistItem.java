package com.indie.catalog.domain.artist.dto.response;

import com.indie.catalog.domain.artist.entity.Genre;

import java.util.List;

public record ArtistItem(long id, String name, List<Genre> genres, Integer debutYear, String profileUrl) {}
