package com.indie.catalog.domain.follow.dto.request;

/**
 * 팔로우 탭 정렬 (, 프론트 요구 1-2) - 없으면 RECENT_FOLLOW.
 * RECENT_FOLLOW - 최근에 팔로우한 아티스트(의 항목)부터
 * FOLLOWERS - 팔로워가 많은 아티스트(의 항목)부터
 * UPDATED - 아티스트 목록은 그 아티스트의 새소식(팔로잉 알림)이 가장 최근인 순, 앨범, 굿즈는 항목이 최근에 등록, 수정된 순
 */
public enum FollowSort { RECENT_FOLLOW, FOLLOWERS, UPDATED }
