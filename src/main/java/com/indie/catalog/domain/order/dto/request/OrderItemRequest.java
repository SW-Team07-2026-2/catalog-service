package com.indie.catalog.domain.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(@NotNull @Positive Long productId, @NotNull @Positive @Max(1000) Integer qty) {}
