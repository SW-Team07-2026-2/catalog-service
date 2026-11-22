package com.indie.catalog.domain.artist.entity;

/**
 * 11/22 아티스트 성별 (프론트 필터) — MIXED 는 남녀가 함께 있는 팀(혼성).
 * 🔴 값을 넣거나 빼면 V2__catalog_values.sql 의 ck_artist_gender 도 함께 고칩니다.
 */
public enum ArtistGender { MALE, FEMALE, MIXED }
