package com.indie.catalog.domain.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.indie.catalog.domain.product.entity.OptionSize;
import com.indie.catalog.domain.product.entity.ProductEntity;
import com.indie.catalog.domain.product.entity.VinylColor;

import java.time.OffsetDateTime;

/** 장바구니 한 줄 — price 는 지금 가격 (주문할 때 다시 계산). stockQty < qty 면 그 수량으로는 주문할 수 없습니다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartLine(long optionId, long productId, ProductEntity.Type productType, String productName, String optionName,
                       VinylColor vinylColor, OptionSize size, String thumbnailUrl, int price, int qty, int stockQty,
                       boolean soldOut, OffsetDateTime addedAt) {}
