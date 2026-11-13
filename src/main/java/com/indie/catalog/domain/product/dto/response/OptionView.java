package com.indie.catalog.domain.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.VinylColor;

import java.time.OffsetDateTime;

/** 판매 단위 — vinylColor 는 LP, size 는 굿즈 옵션에만 있습니다. soldOut · stockUpdatedAt 은 "품절" · "n시간 전 갱신" 표시용 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OptionView(long id, String name, VinylColor vinylColor, OptionSize size, int price, int qty, boolean soldOut,
                         OffsetDateTime stockUpdatedAt) {}
