package com.indie.catalog.domain.product.dto.response;

import java.time.OffsetDateTime;

/** soldOut · stockUpdatedAt — "품절" · "n시간 전 갱신" 표시용 (08 §4) */
public record ProductView(long id, String productType, long refId, String name, int price, String thumbnailUrl,
                          String purchaseUrl, int qty, boolean soldOut, OffsetDateTime stockUpdatedAt) {}
