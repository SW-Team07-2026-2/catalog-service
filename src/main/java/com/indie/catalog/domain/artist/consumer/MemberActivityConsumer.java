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
 * Member -> Kafka member.activity -> Catalog 팔로워 수 갱신.
 * FOLLOW와 UNFOLLOW 이벤트에서 Member의 현재 수로 덮어써 중복 수신을 처리합니다.
 */
@Component
public class MemberActivityConsumer {

    private final FollowerCountSync followerCountSync;
    private final ObjectMapper objectMapper;

    public MemberActivityConsumer(FollowerCountSync followerCountSync, ObjectMapper objectMapper) {
        this.followerCountSync = followerCountSync;
        this.objectMapper = objectMapper;
    }

    /**
     * 팔로우 변경 이벤트를 받아 해당 아티스트의 팔로워 수를 갱신합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
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
