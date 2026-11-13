package com.indie.catalog.domain.album.repository;

import com.indie.catalog.domain.album.entity.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlbumJpaRepository extends JpaRepository<AlbumEntity, Long> {

    Optional<AlbumEntity> findBySourceAndExternalId(String source, String externalId);
}
