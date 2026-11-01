package com.indie.catalog.domain.product.dto.response;

/** 판매하지 않는 굿즈는 productId · price 가 null */
public record GoodsItem(long id, long artistId, String artistName, String name, String category,
                        Long productId, Integer price, String thumbnailUrl) {}
