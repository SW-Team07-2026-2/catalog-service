package com.indie.catalog.global.common.util;

/**
 * 목록 정렬 (팔로우 탭) - 없으면 목록마다 기본 순서.
 * ARTIST_ORDER - 넘긴 아티스트 ID 순서대로 (최근 팔로우순, 팔로워순은 프론트가 Member 팔로우 목록으로 순서를 정해 넘깁니다)
 * UPDATED - 최근 수정순 (updated_at 내림차순)
 */
public enum ListSort { ARTIST_ORDER, UPDATED }
