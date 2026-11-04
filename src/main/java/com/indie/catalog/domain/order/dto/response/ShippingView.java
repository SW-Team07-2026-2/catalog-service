package com.indie.catalog.domain.order.dto.response;

import com.indie.catalog.domain.order.entity.ShippingSnapshot;

/** 이전 주문(10/28 배송지 도입 전)은 null */
public record ShippingView(String recipientName, String phone, String zonecode, String address, String addressDetail) {

    public static ShippingView of(ShippingSnapshot s) {
        return s == null ? null : new ShippingView(s.recipientName(), s.phone(), s.zonecode(), s.address(), s.addressDetail());
    }
}
