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
 * 아티스트 상세를 캐시용 Redis에 JSON으로 저장합니다.
 * 키는 catalog:cache:artist:v4:{id}, 보관 시간은 app.cache.artist-ttl-seconds를 사용합니다.
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
