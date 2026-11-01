package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.ProductEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 판매 상품 저장소 포트 — 구현은 ProductJpaRepository. (product_type, ref_id) 가 유일 */
public interface ProductRepository {

    Optional<ProductEntity> findById(Long id);

    List<ProductEntity> findAllById(Iterable<Long> ids);

    Optional<ProductEntity> findByProductTypeAndRefId(ProductEntity.Type productType, Long refId);

    List<ProductEntity> findByProductTypeAndRefIdIn(ProductEntity.Type productType, Collection<Long> refIds);

    ProductEntity save(ProductEntity product);
}
