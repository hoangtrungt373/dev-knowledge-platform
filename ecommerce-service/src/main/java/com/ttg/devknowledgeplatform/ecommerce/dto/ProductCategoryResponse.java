package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** REST response shape for {@code ProductCategory}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductCategoryResponse {

    private Integer id;
    private String name;
    private String slug;
    private Instant createdAt;
    private Instant updatedAt;
}
