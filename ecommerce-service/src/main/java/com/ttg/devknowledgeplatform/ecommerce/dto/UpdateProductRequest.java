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

    /** WYSIWYG-editor-authored HTML; sanitized down to a safe allowlist in {@code ProductServiceImpl} before persisting — see {@code ProductDescriptionSanitizer}. */
    @Size(max = 50_000, message = "Description must not exceed 50,000 characters")
    private String description;

    @NotNull(message = "Product category is required")
    private Integer productCategoryId;
}
