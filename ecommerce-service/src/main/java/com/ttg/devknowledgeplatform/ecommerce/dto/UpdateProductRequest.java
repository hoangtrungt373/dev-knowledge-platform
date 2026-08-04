package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** Request payload to update a {@code Product}'s basic fields (not its variants/images). */
@Data
public class UpdateProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    private String description;

    @NotNull(message = "Product category is required")
    private Integer productCategoryId;
}
