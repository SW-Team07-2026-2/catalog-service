package com.indie.catalog.domain.order.dto.response;

import com.indie.catalog.domain.order.entity.ShippingSnapshot;

public record ShippingView(String recipientName, String phone, String zonecode, String address, String addressDetail) {

    public static ShippingView of(ShippingSnapshot s) {
        return new ShippingView(s.recipientName(), s.phone(), s.zonecode(), s.address(), s.addressDetail());
    }
}
