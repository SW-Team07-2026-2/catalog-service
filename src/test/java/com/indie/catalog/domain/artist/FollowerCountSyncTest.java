package com.indie.catalog.domain.artist;

import com.indie.catalog.PostgresTest;
import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.domain.follow.client.MemberClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 11/24 artist.follower_count — Member 의 지금 수로 덮어쓰기 · updated_at 은 그대로 · 전체 다시 맞추기는 0 으로 내려가는 것도 */
class FollowerCountSyncTest extends PostgresTest {

    private static final OffsetDateTime UPDATED = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired FollowerCountSync sync;
    @Autowired FakeMember member;

    @BeforeEach
    void resetMember() {
        member.counts.clear();
        member.down = false;
    }

    @Test
    void newArtistStartsAtZero() {
        assertThat(followerCount(artist("fc-0"))).isZero();
    }

    @Test
    void refreshOverwritesWithMemberCountAndKeepsUpdatedAt() {
        long a = artist("fc-1");
        long b = artist("fc-2");
        member.counts.put(a, 3L);

        assertThat(sync.refresh(List.of(a, b))).isEqualTo(1);   // b 는 0 그대로 — 바뀐 행 아님
        assertThat(followerCount(a)).isEqualTo(3);
        assertThat(followerCount(b)).isZero();
        assertThat(sync.refresh(List.of(a))).isZero();          // 같은 이벤트가 다시 와도 결과 같음
        assertThat(jdbc.queryForObject("SELECT updated_at FROM artist WHERE id = ?", OffsetDateTime.class, a))
                .isAtSameInstantAs(UPDATED);                    // 팔로우 탭 업데이트순 기준은 안 바뀜
    }

    @Test
    void refreshAllFixesEveryArtistIncludingDropToZero() {
        long a = artist("fc-3");
        long b = artist("fc-4");
        jdbc.update("UPDATE artist SET follower_count = 5 WHERE id = ?", a);   // 팔로워가 탈퇴해 follow 가 지워진 경우
        member.counts.put(b, 2L);

        sync.refreshAll();

        assertThat(followerCount(a)).isZero();
        assertThat(followerCount(b)).isEqualTo(2);
    }

    @Test
    void memberDownIsUnavailable() {
        long a = artist("fc-5");
        member.down = true;

        assertThatThrownBy(() -> sync.refresh(List.of(a))).isInstanceOf(MemberClient.Unavailable.class);
        assertThat(followerCount(a)).isZero();
    }

    private long artist(String externalId) {
        return jdbc.queryForObject("INSERT INTO artist (name, source, external_id, updated_at) VALUES (?, 'TEST', ?, ?) RETURNING id",
                Long.class, "팔로워 " + externalId, externalId, UPDATED);
    }

    private long followerCount(long artistId) {
        return jdbc.queryForObject("SELECT follower_count FROM artist WHERE id = ?", Long.class, artistId);
    }
}
