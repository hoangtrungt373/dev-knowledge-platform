package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
    /** Ids only, not names/full objects — matches {@code content-service}'s own {@code QuestionAnswerResponse.tagIds}. */
    private Set<Integer> tagIds;
    private Instant createdAt;
    private Instant updatedAt;
}
