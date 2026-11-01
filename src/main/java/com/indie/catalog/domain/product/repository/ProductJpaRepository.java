package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, ProductRepository {
}
