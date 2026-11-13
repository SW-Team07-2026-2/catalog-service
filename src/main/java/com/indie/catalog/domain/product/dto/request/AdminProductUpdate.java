package com.indie.catalog.domain.product.dto.request;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 관리자 상품 수정 — 보낸 값만 바꿉니다 (null 은 그대로). category 는 굿즈에만, releasedAt 은 피지컬 앨범에만 */
public record AdminProductUpdate(@Size(min = 1, max = 255) String name,
                                 @Size(max = 512) String thumbnailUrl,
                                 @Size(max = 1024) String purchaseUrl,
                                 GoodsCategory category,
                                 LocalDate releasedAt) {}
