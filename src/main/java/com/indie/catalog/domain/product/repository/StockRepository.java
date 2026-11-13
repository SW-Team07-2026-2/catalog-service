package com.indie.catalog.domain.product.repository;

import com.indie.catalog.domain.product.entity.StockEntity;

import java.util.List;
import java.util.Optional;

/** 재고 저장소 포트 — 구현은 StockRepositoryAdapter. 키는 옵션 id */
public interface StockRepository {

    List<StockEntity> findAllById(Iterable<Long> optionIds);

    StockEntity save(StockEntity stock);

    /**
     * ★ 주문 차감 — 원자적. 1 = 성공 · 0 = 품절 (11 §5).
     * 같은 행에 대한 동시 차감은 DB 의 행 락이 직렬화합니다 (조건부 UPDATE qty >= n) — Redis 락도 대기열도 필요 없습니다.
     */
    int decrement(long optionId, int qty);

    /** 보상 복구 — 주문 취소 · 만료 · 환불 */
    int restore(long optionId, int qty);

    /** 입고(수집) 때 수량을 덮어쓰는 동안 주문 차감과 엇갈리지 않게 잠급니다 */
    Optional<StockEntity> findForUpdate(Long optionId);
}
