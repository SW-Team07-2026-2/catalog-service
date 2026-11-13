package com.indie.catalog.domain.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.indie.catalog.domain.product.entity.GoodsCategory;
import com.indie.catalog.domain.product.entity.PhysicalFormat;
import com.indie.catalog.domain.product.entity.ProductEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * 상품 상세 — 피지컬 앨범과 굿즈를 한 모양으로. 종류에 없는 값은 빠집니다
 * (피지컬 앨범: albumId · albumTitle · format · releasedAt / 굿즈: category).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetail(long id, ProductEntity.Type productType, String name, String thumbnailUrl, String purchaseUrl,
                            Long artistId, String artistName, Long albumId, String albumTitle, PhysicalFormat format,
                            LocalDate releasedAt, GoodsCategory category, List<OptionView> options) {}
