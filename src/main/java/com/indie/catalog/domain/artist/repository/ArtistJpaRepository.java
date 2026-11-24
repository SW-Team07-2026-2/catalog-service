package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtistJpaRepository extends JpaRepository<ArtistEntity, Long> {

    Optional<ArtistEntity> findBySourceAndExternalId(String source, String externalId);

    /** 11/24 팔로워 수 사본 — 값이 바뀐 행만 · updated_at 은 그대로 (팔로우 탭 업데이트순 기준이라) */
    @Modifying
    @Query(value = "UPDATE artist SET follower_count = :count WHERE id = :id AND follower_count <> :count", nativeQuery = true)
    int updateFollowerCount(@Param("id") long id, @Param("count") long count);
}
