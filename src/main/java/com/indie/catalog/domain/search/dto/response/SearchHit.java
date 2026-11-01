package com.indie.catalog.domain.search.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHit(String type, long id, String title, Long albumId, double score) {}
