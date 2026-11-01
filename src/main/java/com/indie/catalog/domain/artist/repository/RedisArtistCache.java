package com.indie.catalog.domain.artist.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

/** 캐시용 Redis(allkeys-lru)는 죽을 수 있습니다 — 모든 실패는 로그만 남기고 넘어갑니다 (fail-open) */
@Component
public class RedisArtistCache implements ArtistCache {

    private static final Logger log = LoggerFactory.getLogger(RedisArtistCache.class);
    private static final String PREFIX = "catalog:cache:artist:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisArtistCache(StringRedisTemplate redis, ObjectMapper objectMapper, CatalogProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(props.cache().artistTtlSeconds());
    }

    @Override
    public Optional<ArtistDetail> get(long id) {
        try {
            String json = redis.opsForValue().get(PREFIX + id);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, ArtistDetail.class));
        } catch (Exception e) {
            log.warn("artist_cache_get_failed id={} error={}", id, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(ArtistDetail detail) {
        try {
            redis.opsForValue().set(PREFIX + detail.id(), objectMapper.writeValueAsString(detail), ttl);
        } catch (Exception e) {
            log.warn("artist_cache_put_failed id={} error={}", detail.id(), e.toString());
        }
    }

    /** 지우기에 실패해도 TTL(10분)이 상한입니다 */
    @Override
    public void evictAfterCommit(long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(id);
            }
        });
    }

    private void evict(long id) {
        try {
            redis.delete(PREFIX + id);
        } catch (Exception e) {
            log.warn("artist_cache_evict_failed id={} error={}", id, e.toString());
        }
    }
}
