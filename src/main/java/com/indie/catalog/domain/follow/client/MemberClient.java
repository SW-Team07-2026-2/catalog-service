package com.indie.catalog.domain.follow.client;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Member 호출 포트 (11/19 팔로우 탭 · 11/24 아티스트 팔로워 수) — 구현은 HttpMemberClient (내부 토큰 iss=catalog · 타임아웃 3초).
 * 불통 · 5xx · 타임아웃은 전부 Unavailable.
 */
public interface MemberClient {

    /** 회원의 팔로우 전부 — 최근 팔로우순 */
    List<FollowedArtist> follows(long userId);

    /** 11/24 아티스트마다 지금 팔로워 수 (artistId → 수 · 팔로워가 없으면 0) — 한 번에 1~500 개 */
    Map<Long, Long> followerCounts(Collection<Long> artistIds);

    /** followerCount — 그 아티스트의 지금 팔로워 수 */
    record FollowedArtist(long artistId, OffsetDateTime followedAt, long followerCount) {}

    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("member follows failed: " + cause.getMessage(), cause);
        }
    }
}
