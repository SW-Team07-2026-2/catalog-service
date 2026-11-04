package com.indie.catalog.domain.ingest.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LpUpsert(@NotBlank @Size(max = 128) String externalId,
                       @Size(max = 128) String sourceHash,
                       @Size(max = 50) String pressType,
                       LocalDate releasedAt,
                       @Valid SaleUpsert sale) {}
