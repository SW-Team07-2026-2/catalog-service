package com.indie.catalog.domain.product.dto.request;

import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 상품 등록 요청. GOODS는 artistId와 category, PHYSICAL_ALBUM은 albumId와 format이 필수입니다.
 * 피지컬 앨범의 대표 이미지가 없으면 앨범 이미지를 사용합니다. 상세 설명은 선택입니다.
 */
public record AdminProductCreate(@NotNull ProductEntity.Type productType,
                                 @NotBlank @Size(max = 255) String name,
                                 @Size(max = 512) String thumbnailUrl,
                                 @Positive Long artistId,
                                 GoodsCategory category,
                                 @Positive Long albumId,
                                 PhysicalFormat format,
                                 LocalDate releasedAt,
                                 @NotEmpty List<@Valid @NotNull AdminOption> options,
                                 @Size(max = 1000) String description) {}
