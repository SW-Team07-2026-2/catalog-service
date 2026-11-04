package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.LabelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelJpaRepository extends JpaRepository<LabelEntity, Long>, LabelRepository {
}
