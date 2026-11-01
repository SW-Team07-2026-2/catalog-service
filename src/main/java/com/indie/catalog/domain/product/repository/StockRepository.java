package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.StockEntity;

import java.util.List;
import java.util.Optional;

/** 재고 저장소 포트 — 구현은 StockJpaRepository */
public interface StockRepository {

    Optional<StockEntity> findById(Long productId);

    List<StockEntity> findAllById(Iterable<Long> productIds);

    StockEntity save(StockEntity stock);

    /**
     * ★ 주문 차감 — 락 없이 원자적. 1 = 성공 · 0 = 품절 (11 §5).
     * 같은 행에 대한 동시 차감은 DB 가 직렬화해야 합니다 (구현: 조건부 UPDATE qty >= n).
     */
    int decrement(long productId, int qty);

    /** 보상 복구 — 🔴 재입고 이벤트를 내지 않습니다 (주문 실패 · 취소 · 만료마다 알림이 가면 스팸) */
    int restore(long productId, int qty);

    /** 입고(upsert) 시 이전 수량을 확인하고 덮어쓰는 동안 주문 차감과 엇갈리지 않게 잠급니다 */
    Optional<StockEntity> findForUpdate(Long productId);
}
