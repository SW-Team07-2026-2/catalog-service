package com.indie.catalog.domain.artist.scheduler;

import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.domain.follow.client.MemberClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 11/24 artist.follower_count 전체 다시 맞추기 — 기동 1분 뒤 · 그 뒤 1시간마다 (로직은 FollowerCountSync.refreshAll).
 * 이벤트로 못 맞추는 것: 탈퇴(follow 가 CASCADE 로 지워져 UNFOLLOW 가 안 나감) · Member 불통 중 DLT 로 간 이벤트 · 첫 배포.
 * replica 마다 돌아도 덮어쓰기라 결과가 같습니다.
 */
@Component
public class FollowerCountSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FollowerCountSyncJob.class);

    private final FollowerCountSync followerCountSync;

    public FollowerCountSyncJob(FollowerCountSync followerCountSync) {
        this.followerCountSync = followerCountSync;
    }

    @Scheduled(initialDelayString = "${app.follower-count.initial-delay-ms}", fixedDelayString = "${app.follower-count.sync-interval-ms}")
    public void sync() {
        try {
            int changed = followerCountSync.refreshAll();
            if (changed > 0) log.info("follower_count_synced changed={}", changed);
        } catch (MemberClient.Unavailable e) {
            log.warn("follower_count_sync_skipped reason=member_unavailable error={}", e.toString());   // 다음 주기에 다시
        }
    }
}
