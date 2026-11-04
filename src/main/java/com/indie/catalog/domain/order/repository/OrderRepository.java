package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import org.springframework.data.domain.Limit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 저장소 포트 — 구현은 OrderJpaRepository.
 * 상태 전이는 조건부 UPDATE 로만 합니다 — 중복 이벤트 · 동시 취소에도 한 번만 전이되고, 결과(0/1)로 판단합니다.
 */
public interface OrderRepository {

    Optional<PurchaseOrderEntity> findById(Long id);

    /** ✅ 소유권은 항상 조회 조건에 — id 만으로 꺼내면 남의 주문이 보입니다 (IDOR) */
    Optional<PurchaseOrderEntity> findByIdAndUserId(Long id, Long userId);

    List<PurchaseOrderEntity> findByUserIdOrderByIdDesc(Long userId, Limit limit);

    List<PurchaseOrderEntity> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Limit limit);

    PurchaseOrderEntity save(PurchaseOrderEntity order);

    int attachPgOrderId(long id, String pgOrderId);

    /** PENDING → CONFIRMED. 0 = 이미 전이됨 (중복 이벤트 · 취소된 주문) */
    int confirm(long id);

    /** PENDING · CONFIRMED → CANCELLED. 1 일 때만 재고를 복구합니다 — 두 번 복구되지 않게 */
    int cancel(long id);

    /** 결제창 이탈 — before 이전에 만든 PENDING 주문 id (오래된 순) */
    List<Long> findExpiredPending(OffsetDateTime before, int limit);
}
