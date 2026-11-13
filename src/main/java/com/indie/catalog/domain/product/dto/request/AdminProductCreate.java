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
 * 관리자 상품 등록 — productType 에 따라 필요한 값이 다릅니다 (AdminProductService 가 검사).
 * <pre>
 * GOODS            artistId · category 필수
 * PHYSICAL_ALBUM   albumId · format 필수 · releasedAt 선택 · thumbnailUrl 이 없으면 앨범 이미지
 * </pre>
 */
public record AdminProductCreate(@NotNull ProductEntity.Type productType,
                                 @NotBlank @Size(max = 255) String name,
                                 @Size(max = 512) String thumbnailUrl,
                                 @Size(max = 1024) String purchaseUrl,
                                 @Positive Long artistId,
                                 GoodsCategory category,
                                 @Positive Long albumId,
                                 PhysicalFormat format,
                                 LocalDate releasedAt,
                                 @NotEmpty List<@Valid @NotNull AdminOption> options) {}
