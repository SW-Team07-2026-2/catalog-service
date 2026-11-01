package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<PurchaseOrderEntity, Long>, OrderRepository {

    @Override
    @Modifying
    @Query(value = "UPDATE purchase_order SET pg_order_id = :pgOrderId WHERE id = :id", nativeQuery = true)
    int attachPgOrderId(@Param("id") long id, @Param("pgOrderId") String pgOrderId);

    @Override
    @Modifying
    @Query(value = "UPDATE purchase_order SET status = 'CONFIRMED' WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int confirm(@Param("id") long id);

    @Override
    @Modifying
    @Query(value = "UPDATE purchase_order SET status = 'CANCELLED' WHERE id = :id AND status IN ('PENDING', 'CONFIRMED')",
            nativeQuery = true)
    int cancel(@Param("id") long id);

    @Override
    @Query(value = """
            SELECT id FROM purchase_order
            WHERE status = 'PENDING' AND created_at < :before
            ORDER BY created_at LIMIT :limit
            """, nativeQuery = true)
    List<Long> findExpiredPending(@Param("before") OffsetDateTime before, @Param("limit") int limit);
}
