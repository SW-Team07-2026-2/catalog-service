package com.indie.catalog.domain.product.dto.request;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 상품 수정 — 보낸 값만 바꿉니다 (null 은 그대로). category 는 굿즈에만, releasedAt 은 피지컬 앨범에만.
 * 11/26 description(상세 설명 · 빈 글은 지움) · detailImageUrls(상세 이미지 · 보낸 목록으로 바꿈 · [] 는 지움)는 둘 다
 */
public record AdminProductUpdate(@Size(min = 1, max = 255) String name,
                                 @Size(max = 512) String thumbnailUrl,
                                 GoodsCategory category,
                                 LocalDate releasedAt,
                                 @Size(max = 1000) String description,
                                 @Size(max = 10) List<@NotBlank @Size(max = 512) String> detailImageUrls) {}
