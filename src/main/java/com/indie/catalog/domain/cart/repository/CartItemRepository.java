package com.indie.catalog.domain.cart.repository;

import com.indie.catalog.domain.cart.entity.CartItemEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/** 장바구니 저장소 포트 — 구현은 CartItemRepositoryAdapter. 모든 조회 · 삭제 조건에 user_id 가 들어갑니다 (남의 장바구니 접근 방지) */
public interface CartItemRepository {

    /** 최근에 담은 순 */
    List<CartItemEntity> findByUserId(long userId);

    /** 담기 — 이미 담긴 옵션이면 수량만 바꿉니다 (담은 시각은 그대로) */
    void put(long userId, long optionId, int qty, OffsetDateTime now);

    boolean delete(long userId, long optionId);

    /** 주문이 확정된 옵션을 장바구니에서 뺍니다 */
    long deleteOptions(long userId, Collection<Long> optionIds);

    /** 장바구니 비우기 · 회원 탈퇴 */
    long deleteByUserId(long userId);
}
