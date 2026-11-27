package com.indie.catalog.domain.artist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.consumer.MemberActivityConsumer;
import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.domain.follow.client.MemberClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** member.activity - FOLLOW, UNFOLLOW 만 그 아티스트를 다시 받음, Member 불통이면 커밋하지 않고 예외 (재시도 -> DLT) */
class MemberActivityConsumerTest {

    private final FollowerCountSync sync = mock(FollowerCountSync.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final MemberActivityConsumer consumer = new MemberActivityConsumer(sync, new ObjectMapper());

    @Test
    void followAndUnfollowRefreshThatArtist() throws Exception {
        consumer.onActivity(event("FOLLOW", 12), ack);
        consumer.onActivity(event("UNFOLLOW", 13), ack);

        verify(sync).refresh(List.of(12L));
        verify(sync).refresh(List.of(13L));
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void otherActivityIsAcknowledgedWithoutRefresh() throws Exception {
        consumer.onActivity(event("COLLECTION_ADD", 12), ack);

        verify(sync, never()).refresh(any());
        verify(ack).acknowledge();
    }

    @Test
    void memberDownIsNotAcknowledged() {
        when(sync.refresh(any())).thenThrow(new MemberClient.Unavailable(new java.io.IOException("connection refused")));

        assertThatThrownBy(() -> consumer.onActivity(event("FOLLOW", 12), ack)).isInstanceOf(MemberClient.Unavailable.class);
        verify(ack, never()).acknowledge();
    }

    private static ConsumerRecord<String, String> event(String type, long artistId) {
        String json = """
                {"eventId":"0193a1b2-0000-7000-8000-000000000001","type":"member.activity","version":1,
                 "occurredAt":"2026-11-24T03:00:00Z",
                 "payload":{"userId":7,"type":"%s","targetId":%d,"occurredAt":"2026-11-24T03:00:00Z"}}
                """.formatted(type, artistId);
        return new ConsumerRecord<>("member.activity", 0, 0L, "7", json);
    }
}
