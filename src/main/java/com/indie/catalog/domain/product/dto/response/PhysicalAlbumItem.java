package com.indie.catalog.domain.product.dto.response;

import com.indie.catalog.domain.product.entity.PhysicalFormat;

import java.time.LocalDate;

/**
 * 피지컬 앨범 목록 항목 — id 는 상품 id. minPrice 는 옵션 중 가장 낮은 가격 (옵션이 아직 없으면 null) · 옵션별 재고는 상품 상세(C8)로.
 * soldOut 은 모든 옵션 재고가 0 이거나 옵션이 없을 때 true (11/18 — 목록에서 품절 표시용)
 */
public record PhysicalAlbumItem(long id, long albumId, String albumTitle, long artistId, String artistName,
                                PhysicalFormat format, String name, LocalDate releasedAt, String thumbnailUrl,
                                Integer minPrice, boolean soldOut) {}
