package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** Nested variant payload used by {@link CreateProductRequest}. */
@Data
public class ProductVariantRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 64, message = "SKU must not exceed 64 characters")
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must not be negative")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity must not be negative")
    private Integer stockQuantity;

    /** Free-form attribute keys (e.g. size/color); must match across every variant of the same product. */
    private Map<String, String> attributes;
}
