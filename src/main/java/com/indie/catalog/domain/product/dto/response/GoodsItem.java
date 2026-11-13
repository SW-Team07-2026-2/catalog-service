package com.indie.catalog.domain.product.dto.response;

import com.indie.catalog.domain.product.entity.GoodsCategory;

/** 굿즈 목록 항목 — id 는 상품 id. minPrice 는 옵션 중 가장 낮은 가격 (옵션이 아직 없으면 null) · 재고는 상품 상세(C8)로 */
public record GoodsItem(long id, long artistId, String artistName, String name, GoodsCategory category,
                        String thumbnailUrl, Integer minPrice) {}
