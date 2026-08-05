package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** Request payload to change a {@code ProductImage}'s position in the gallery (US-1.6). */
@Data
public class UpdateProductImageSortOrderRequest {

    @NotNull(message = "Sort order is required")
    @Min(value = 0, message = "Sort order must not be negative")
    private Integer sortOrder;
}
