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
 * 11/26 description — 상세 설명 (굿즈 · 판본 공통 · 마크다운 · 없으면 빠짐) · 11/27 상세 이미지는 주지 않습니다 (대표 이미지 thumbnailUrl 만)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetail(long id, ProductEntity.Type productType, String name, String thumbnailUrl,
                            Long artistId, String artistName, Long albumId, String albumTitle, PhysicalFormat format,
                            LocalDate releasedAt, GoodsCategory category, String description, List<OptionView> options) {}
