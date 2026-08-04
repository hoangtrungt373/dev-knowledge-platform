package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** Nested image payload used by {@link CreateProductRequest}. */
@Data
public class ProductImageRequest {

    @NotBlank(message = "Storage key is required")
    private String storageKey;

    @NotNull(message = "Sort order is required")
    @Min(value = 0, message = "Sort order must not be negative")
    private Integer sortOrder;
}
