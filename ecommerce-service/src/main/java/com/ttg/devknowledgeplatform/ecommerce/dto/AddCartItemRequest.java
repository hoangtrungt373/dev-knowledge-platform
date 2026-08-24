package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** Request payload to add a variant to the cart (US-2.1). */
@Data
public class AddCartItemRequest {

    @NotNull(message = "Variant id is required")
    private Integer variantId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
