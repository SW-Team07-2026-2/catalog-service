package com.indie.catalog.domain.artist.service;

import com.indie.catalog.domain.artist.repository.ArtistRepository;
import com.indie.catalog.domain.follow.client.MemberClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Member의 팔로워 수를 조회해 Catalog의 artist.follower_count를 갱신합니다.
 * 증감 대신 현재 수로 덮어쓰며 전체 동기화로 탈퇴나 누락 이벤트를 보완합니다.
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

    /**
     * 이 아티스트들의 팔로워 수를 Member 에서 받아 덮어씀. 바뀐 행 수, Member 불통이면 MemberClient.Unavailable
     *
     * @param artistIds 대상 아티스트 ID 목록
     */
    public int refresh(Collection<Long> artistIds) {
        return artistRepository.updateFollowerCounts(memberClient.followerCounts(artistIds));
    }

    /** 전체 - id 순 BATCH 개씩. 바뀐 행 수 */
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
