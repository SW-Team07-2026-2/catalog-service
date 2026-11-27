package com.indie.catalog.global.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Catalog 회원 행동 -> Outbox -> Kafka user.metric -> Member 메트릭 저장.
 * 업무 트랜잭션과 함께 저장하며 트랜잭션이 없으면 새로 엽니다. metricId로 중복 수신을 처리합니다.
 */
@Component
public class UserMetrics {

    public static final String TOPIC = "user.metric";

    /** 같은 회원, 탭, 검색어는 이 구간에 한 번만 - 칩만 바꿔 다시 부를 때 여러 번 세지 않게 */
    static final Duration SEARCH_WINDOW = Duration.ofMinutes(10);

    private final EventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    public UserMetrics(EventPublisher publisher, PlatformTransactionManager transactionManager, Clock clock) {
        this.publisher = publisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 장바구니 수량 변화 - 늘면 CART_ADD, 줄면 CART_REMOVE (값은 변한 수량), 같으면 남기지 않습니다 */
    public void cartChanged(long userId, String productType, long productId, long optionId, int before, int after) {
        if (before == after) {
            return;
        }
        publish(userId, UUID.randomUUID(), productType, productId, after > before ? "CART_ADD" : "CART_REMOVE",
                Math.abs(after - before), Map.of("optionId", optionId));
    }

    /** 검색어 - 로그인했고 검색어가 있는 첫 페이지만 (다음 페이지, 칩만 고른 검색, 비로그인은 남기지 않음). tab 은 대상 종류 */
    public void searched(Long userId, String tab, String q, int page) {
        if (userId == null || q == null || q.isBlank() || page != 0) {
            return;
        }
        String keyword = q.strip();
        long window = clock.millis() / SEARCH_WINDOW.toMillis();
        UUID id = UUID.nameUUIDFromBytes(("search:" + userId + ":" + tab + ":" + keyword + ":" + window)
                .getBytes(StandardCharsets.UTF_8));
        publish(userId, id, tab, null, "SEARCH", 1, Map.of("keyword", keyword));
    }

    private void publish(long userId, UUID metricId, String targetType, Long targetId, String metricType, long value,
                         Map<String, Object> properties) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId);
        p.put("metricId", metricId.toString());
        p.put("targetType", targetType);
        p.put("targetId", targetId);
        p.put("metricType", metricType);
        p.put("metricValue", value);
        p.put("properties", properties);
        tx.executeWithoutResult(status -> publisher.publish(TOPIC, p));
    }
}
