package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** Request payload to set a cart line's quantity; {@code 0} removes it entirely (US-2.2). */
@Data
public class UpdateCartItemRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must not be negative")
    private Integer quantity;
}
