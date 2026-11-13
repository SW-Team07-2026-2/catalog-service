package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.ProductOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionJpaRepository extends JpaRepository<ProductOptionEntity, Long>, ProductOptionRepository {
}
