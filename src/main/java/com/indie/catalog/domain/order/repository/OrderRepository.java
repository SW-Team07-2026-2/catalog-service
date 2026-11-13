package com.indie.catalog.domain.order.repository;

import com.indie.catalog.domain.order.entity.PurchaseOrderEntity;
import com.indie.catalog.global.common.util.Cursor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 저장소 포트 — 구현은 OrderRepositoryAdapter.
 * 상태 전이는 조건부 UPDATE 로만 합니다 — 중복 이벤트 · 동시 취소에도 한 번만 전이되고, 결과(0/1)로 판단합니다.
 */
public interface OrderRepository {

    Optional<PurchaseOrderEntity> findById(Long id);

    /** ✅ 소유권은 항상 조회 조건에 — id 만으로 꺼내면 남의 주문이 보입니다 (IDOR) */
    Optional<PurchaseOrderEntity> findByIdAndUserId(Long id, Long userId);

    /** 내 주문 — 최신순 · 커서(id) 이전 */
    List<PurchaseOrderEntity> findPage(long userId, Cursor cursor, int limit);

    PurchaseOrderEntity save(PurchaseOrderEntity order);

    int attachPgOrderId(long id, String pgOrderId);

    /** PENDING → CONFIRMED. 0 = 이미 전이됨 (중복 이벤트 · 취소된 주문) */
    int confirm(long id);

    /** PENDING · CONFIRMED → CANCELLED. 1 일 때만 재고를 복구합니다 — 두 번 복구되지 않게 */
    int cancel(long id);

    /** PENDING · CONFIRMED → REFUNDED (결제된 주문을 환불). 1 일 때만 재고를 복구합니다 */
    int refund(long id);

    /** CANCELLED → REFUNDED — 먼저 취소로 기록했는데 Payment 가 환불했다고 알려 온 경우. 재고는 이미 복구됐습니다 */
    int refundCancelled(long id);

    /** 결제창 이탈 — before 이전에 만든 PENDING 주문 id (오래된 순) */
    List<Long> findExpiredPending(OffsetDateTime before, int limit);

    /** 회원 탈퇴 — 그 회원의 PENDING 주문 id */
    List<Long> findPendingIdsByUserId(long userId);
}
