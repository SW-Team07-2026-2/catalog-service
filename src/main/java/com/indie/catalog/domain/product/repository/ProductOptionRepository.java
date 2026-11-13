package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.ProductOptionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 상품 옵션 저장소 포트 — 구현은 ProductOptionJpaRepository. (product_id, name) 이 수집 갱신 기준 */
public interface ProductOptionRepository {

    Optional<ProductOptionEntity> findById(Long id);

    List<ProductOptionEntity> findAllById(Iterable<Long> ids);

    List<ProductOptionEntity> findByProductIdOrderById(Long productId);

    List<ProductOptionEntity> findByProductIdInOrderById(Collection<Long> productIds);

    ProductOptionEntity save(ProductOptionEntity option);
}
