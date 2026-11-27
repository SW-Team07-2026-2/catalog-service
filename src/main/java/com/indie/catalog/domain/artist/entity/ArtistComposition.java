package com.indie.catalog.domain.artist.entity;

/**
 * 아티스트 구성 (프론트 필터) - SOLO 솔로, GROUP 팀(밴드, 듀오 포함).
 * 값을 넣거나 빼면 V2__catalog_values.sql 의 ck_artist_composition 도 함께 고칩니다.
 */
public enum ArtistComposition { SOLO, GROUP }
