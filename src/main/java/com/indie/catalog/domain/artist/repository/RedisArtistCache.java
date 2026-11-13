package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

/**
 * 캐시용 Redis(allkeys-lru)는 죽을 수 있습니다 — 모든 실패는 로그만 남기고 넘어갑니다 (fail-open).
 * 값 JSON 은 artistRedis 템플릿의 직렬화기가 맡습니다 (62B) — 깨진 값도 읽기 실패로 보고 DB 로 갑니다.
 */
@Component
public class RedisArtistCache implements ArtistCache {

    private static final Logger log = LoggerFactory.getLogger(RedisArtistCache.class);
    private static final String PREFIX = "catalog:cache:artist:v2:";   // v2 — 응답 모양이 바뀌어 옛 값과 섞이지 않게

    private final RedisTemplate<String, ArtistDetail> artistRedis;
    private final Duration ttl;

    public RedisArtistCache(RedisTemplate<String, ArtistDetail> artistRedis, CatalogProperties props) {
        this.artistRedis = artistRedis;
        this.ttl = Duration.ofSeconds(props.cache().artistTtlSeconds());
    }

    @Override
    public Optional<ArtistDetail> get(long id) {
        try {
            return Optional.ofNullable(artistRedis.opsForValue().get(PREFIX + id));
        } catch (Exception e) {
            log.warn("artist_cache_get_failed id={} error={}", id, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(ArtistDetail detail) {
        try {
            artistRedis.opsForValue().set(PREFIX + detail.id(), detail, ttl);
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
            artistRedis.delete(PREFIX + id);
        } catch (Exception e) {
            log.warn("artist_cache_evict_failed id={} error={}", id, e.toString());
        }
    }
}
