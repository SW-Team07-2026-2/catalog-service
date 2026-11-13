package com.indie.catalog.domain.artist.entity;

/**
 * 장르 — 아티스트 · 앨범 · 트랙이 같이 씁니다 (genres 배열 컬럼).
 * 🔴 값을 넣거나 빼면 V2__catalog_values.sql 의 ck_*_genres CHECK 목록과 concert 의 장르 목록(concert V2 · Genre)도 함께 고칩니다.
 */
public enum Genre {
    INDIE_ROCK, ALTERNATIVE, GARAGE, PUNK, HARDCORE, EMO, METAL, POST_ROCK, SHOEGAZE, PSYCHEDELIC,
    INDIE_POP, DREAM_POP, SYNTH_POP, CITY_POP,
    FOLK, ELECTRONIC, RNB_SOUL, HIPHOP, JAZZ, BLUES, FUNK, SKA_REGGAE, ETC
}
