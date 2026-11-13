package com.indie.catalog.domain.search.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** type — ARTIST · ALBUM · TRACK. albumId 는 트랙에만 있습니다 (트랙은 앨범 화면으로 이동) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHit(String type, long id, String title, Long albumId, double score) {}
