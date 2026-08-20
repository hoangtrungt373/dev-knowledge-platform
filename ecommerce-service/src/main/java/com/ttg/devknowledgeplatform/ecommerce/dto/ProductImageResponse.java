package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/** REST response shape for {@code ProductImage}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductImageResponse {

    private Integer id;
    private String storageKey;
    private Integer sortOrder;
    /** Time-limited presigned GET URL for {@link #storageKey}, resolved by {@code ProductMapper}. */
    private String url;
}
