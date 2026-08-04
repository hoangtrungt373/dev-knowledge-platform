package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** Request payload to create a {@code Product} together with its variants and image gallery. */
@Data
public class CreateProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    private String description;

    @NotNull(message = "Product category is required")
    private Integer productCategoryId;

    @NotEmpty(message = "At least one variant is required")
    @Valid
    private List<ProductVariantRequest> variants;

    @Valid
    private List<ProductImageRequest> images;
}
