package com.indie.catalog.domain.artist.repository;

import com.indie.catalog.domain.artist.entity.LabelEntity;

import java.util.Optional;

/** 레이블 저장소 포트 — 구현은 LabelJpaRepository */
public interface LabelRepository {

    Optional<LabelEntity> findById(Long id);

    Optional<LabelEntity> findByName(String name);

    LabelEntity save(LabelEntity label);
}
