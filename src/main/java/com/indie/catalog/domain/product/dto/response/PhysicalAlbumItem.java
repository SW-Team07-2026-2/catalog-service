package com.indie.catalog.domain.product.dto.response;

import com.indie.catalog.domain.product.entity.PhysicalFormat;

import java.time.LocalDate;

/** 피지컬 앨범 목록 항목 — id 는 상품 id. minPrice 는 옵션 중 가장 낮은 가격 (옵션이 아직 없으면 null) · 재고는 상품 상세(C8)로 */
public record PhysicalAlbumItem(long id, long albumId, String albumTitle, long artistId, String artistName,
                                PhysicalFormat format, String name, LocalDate releasedAt, String thumbnailUrl,
                                Integer minPrice) {}
