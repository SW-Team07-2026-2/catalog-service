package com.indie.catalog.domain.follow.client;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Catalog -> Member 팔로우와 팔로워 수 조회 포트. 구현은 HttpMemberClient입니다.
 * 연결 실패, 5xx, 타임아웃은 Unavailable로 전달합니다.
 */
public interface MemberClient {

    /**
     * 회원의 팔로우 전부 - 최근 팔로우순
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    List<FollowedArtist> follows(long userId);

    /**
     * 아티스트마다 지금 팔로워 수 (artistId -> 수, 팔로워가 없으면 0) - 한 번에 1~500 개
     *
     * @param artistIds 대상 아티스트 ID 목록
     */
    Map<Long, Long> followerCounts(Collection<Long> artistIds);

    /** followerCount - 그 아티스트의 지금 팔로워 수 */
    record FollowedArtist(long artistId, OffsetDateTime followedAt, long followerCount) {}

    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super("member follows failed: " + cause.getMessage(), cause);
        }
    }
}
