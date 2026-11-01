package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.StockEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockEntity, Long>, StockRepository {

    /** PostgreSQL 의 row lock 이 같은 행 UPDATE 를 직렬화하므로 Redis 락도 대기열도 필요 없습니다 */
    @Override
    @Modifying
    @Query(value = "UPDATE stock SET qty = qty - :qty, updated_at = now() WHERE product_id = :productId AND qty >= :qty",
            nativeQuery = true)
    int decrement(@Param("productId") long productId, @Param("qty") int qty);

    @Override
    @Modifying
    @Query(value = "UPDATE stock SET qty = qty + :qty, updated_at = now() WHERE product_id = :productId", nativeQuery = true)
    int restore(@Param("productId") long productId, @Param("qty") int qty);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockEntity s where s.productId = :productId")
    Optional<StockEntity> findForUpdate(@Param("productId") Long productId);
}
