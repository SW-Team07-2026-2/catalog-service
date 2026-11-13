package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.GoodsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsJpaRepository extends JpaRepository<GoodsEntity, Long> {

    Optional<GoodsEntity> findBySourceAndExternalId(String source, String externalId);
}
