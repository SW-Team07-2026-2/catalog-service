package com.indie.catalog.domain.artist.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * member.activity (Member) — FOLLOW · UNFOLLOW 이면 그 아티스트의 팔로워 수를 다시 받아 artist.follower_count 에 덮어씀 (11/24).
 * 멱등 — 수를 더하지 않고 Member 의 지금 수로 덮어씁니다. Member 불통이면 재시도 2회 뒤 DLT · 1시간마다 전체 다시 맞추기가 메웁니다.
 */
@Component
public class MemberActivityConsumer {

    private final FollowerCountSync followerCountSync;
    private final ObjectMapper objectMapper;

    public MemberActivityConsumer(FollowerCountSync followerCountSync, ObjectMapper objectMapper) {
        this.followerCountSync = followerCountSync;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "member.activity")
    public void onActivity(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        String type = e.text("type");
        if ("FOLLOW".equals(type) || "UNFOLLOW".equals(type)) {
            followerCountSync.refresh(List.of(e.requiredLong("targetId")));
        }
        ack.acknowledge();
    }
}
