package com.indie.catalog.domain.artist.service;

import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.follow.client.MemberClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 11/24 artist.follower_count — Member follow 행 수의 사본 (인기순 정렬용).
 * 더하고 빼지 않고 Member 의 지금 수로 덮어씁니다 — 같은 이벤트가 여러 번 와도 · 순서가 바뀌어도 결과가 같습니다.
 * 탈퇴(follow 가 CASCADE 로 지워져 UNFOLLOW 가 안 나감) · 놓친 이벤트 · 첫 배포 채우기는 refreshAll (FollowerCountSyncJob).
 */
@Service
public class FollowerCountSync {

    /** Member 한 번에 받는 상한과 같게 */
    static final int BATCH = 500;

    private final MemberClient memberClient;
    private final ArtistRepository artistRepository;

    public FollowerCountSync(MemberClient memberClient, ArtistRepository artistRepository) {
        this.memberClient = memberClient;
        this.artistRepository = artistRepository;
    }

    /** 이 아티스트들의 팔로워 수를 Member 에서 받아 덮어씀. 바뀐 행 수 · Member 불통이면 MemberClient.Unavailable */
    public int refresh(Collection<Long> artistIds) {
        return artistRepository.updateFollowerCounts(memberClient.followerCounts(artistIds));
    }

    /** 전체 — id 순 BATCH 개씩. 바뀐 행 수 */
    public int refreshAll() {
        int changed = 0;
        long afterId = 0;
        List<Long> ids;
        while (!(ids = artistRepository.findIdsAfter(afterId, BATCH)).isEmpty()) {
            changed += refresh(ids);
            afterId = ids.get(ids.size() - 1);
        }
        return changed;
    }
}
