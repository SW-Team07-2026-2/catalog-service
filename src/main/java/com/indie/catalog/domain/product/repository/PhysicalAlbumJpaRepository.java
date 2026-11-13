package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.PhysicalAlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhysicalAlbumJpaRepository extends JpaRepository<PhysicalAlbumEntity, Long> {

    Optional<PhysicalAlbumEntity> findBySourceAndExternalId(String source, String externalId);
}
