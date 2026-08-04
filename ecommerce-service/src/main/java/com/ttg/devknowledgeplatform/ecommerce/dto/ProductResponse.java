package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** REST response shape for {@code Product}, including its variants and image gallery. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

    private Integer id;
    private String name;
    private String description;
    private String slug;
    private boolean active;
    private Integer productCategoryId;
    private String categoryName;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;
    private Instant createdAt;
    private Instant updatedAt;
}
