package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.ProductEntity;

import java.util.List;
import java.util.Optional;

/** 상품(부모) 저장소 포트 — 구현은 ProductJpaRepository. 피지컬 앨범 · 굿즈를 종류와 상관없이 id 로 꺼낼 때 씁니다 */
public interface ProductRepository {

    Optional<ProductEntity> findById(Long id);

    List<ProductEntity> findAllById(Iterable<Long> ids);
}
