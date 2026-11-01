package com.indie.catalog.domain.order.dto.request;

import com.indie.catalog.domain.order.entity.ShippingSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 받는 곳 — Member 배송지(M19)와 같은 모양. 주소 검색은 프론트의 카카오 우편번호 서비스라 서버는 형식만 봅니다 */
public record ShippingRequest(@NotBlank @Size(max = 20) String recipientName,
                              @NotNull @Pattern(regexp = "0\\d{1,2}-?\\d{3,4}-?\\d{4}") String phone,
                              @NotNull @Pattern(regexp = "\\d{5}") String zonecode,
                              @NotBlank @Size(max = 200) String address,
                              @Size(max = 100) String addressDetail) {

    public ShippingSnapshot toSnapshot() {
        return new ShippingSnapshot(recipientName, phone, zonecode, address, addressDetail);
    }
}
