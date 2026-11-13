package com.indie.catalog.domain.order.dto.response;

import com.indie.catalog.domain.product.entity.ProductEntity;

/** price 는 주문 시점 가격 */
public record OrderItemView(long optionId, long productId, ProductEntity.Type productType, String productName,
                            String optionName, int qty, int price) {}
