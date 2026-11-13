package com.indie.catalog.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app")
public record CatalogProperties(Internal internal, InternalToken internalToken, Payment payment, Order order,
                                Page page, Cache cache, Outbox outbox, Event event) {

    /** ingestIssuer · ingestPaths — Gateway 수집 토큰을 받는 곳 (docs/22 S1) */
    public record Internal(String expectedAudience, List<String> allowedIssuers, long clockSkewSeconds,
                           String ingestIssuer, List<String> ingestPaths) {}

    public record InternalToken(String issuer, long ttlSeconds) {}

    public record Payment(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {}

    public record Order(int maxItems, int maxQty, long pendingTtlMinutes, long expiryIntervalMs) {}

    public record Page(int size, int maxSize) {}

    public record Cache(long artistTtlSeconds) {}

    public record Outbox(long relayIntervalMs, int batchSize) {}

    /** releaseWindowDays — 발매일이 이 기간 안일 때만 발매 알림 이벤트를 냅니다 (옛 음반을 처음 수집할 때 알림이 가지 않게) */
    public record Event(int releaseWindowDays) {}
}
