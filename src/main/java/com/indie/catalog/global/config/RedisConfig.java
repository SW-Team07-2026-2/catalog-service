package com.indie.catalog.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.dto.response.ArtistDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 캐시용 Redis 하나 (allkeys-lru) — 연결은 spring.data.redis.* (Boot 자동 설정).
 * <pre>
 * artistRedis   catalog:cache:artist:v2:{id}   → ArtistDetail JSON   TTL app.cache.artist-ttl-seconds
 * </pre>
 * 62B — 값 JSON 은 타입별 템플릿의 직렬화기가 맡습니다 (ObjectMapper 직접 호출 없음)
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ArtistDetail> artistRedis(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        RedisTemplate<String, ArtistDetail> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, ArtistDetail.class));
        template.afterPropertiesSet();
        return template;
    }
}
