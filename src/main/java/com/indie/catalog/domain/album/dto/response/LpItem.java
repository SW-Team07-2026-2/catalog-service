package com.indie.catalog.domain.album.dto.response;

import java.time.LocalDate;

/** 판매하지 않는 LP 는 productId · price 가 null — 재고는 C8 로 (캐시 대상과 분리) */
public record LpItem(long id, long albumId, String albumTitle, long artistId, String artistName, String pressType,
                     LocalDate releasedAt, String thumbnailUrl, Long productId, Integer price) {}
