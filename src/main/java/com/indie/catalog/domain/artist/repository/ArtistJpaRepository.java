package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.ArtistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistJpaRepository extends JpaRepository<ArtistEntity, Long> {

    Optional<ArtistEntity> findBySourceAndExternalId(String source, String externalId);
}
