package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.TrackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackJpaRepository extends JpaRepository<TrackEntity, Long>, TrackRepository {
}
