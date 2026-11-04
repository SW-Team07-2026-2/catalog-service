package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GoodsUpsert(@NotBlank @Size(max = 128) String externalId,
                          @Size(max = 128) String sourceHash,
                          @NotBlank @Size(max = 200) String name,
                          @Size(max = 32) String category,
                          @Size(max = 512) String thumbnailUrl,
                          @Valid SaleUpsert sale) {}
