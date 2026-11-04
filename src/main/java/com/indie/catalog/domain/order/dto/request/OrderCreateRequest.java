package com.indie.catalog.domain.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderCreateRequest(@NotEmpty List<@Valid @NotNull OrderItemRequest> items,
                                 @NotNull @Valid ShippingRequest shipping) {}
