package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** Request payload to rename a {@code ProductAttribute} and/or replace its value list. */
@Data
public class UpdateProductAttributeRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    private String name;

    /** The attribute's new, complete controlled vocabulary, in display order — must be non-empty. */
    @NotEmpty(message = "At least one value is required")
    private List<@NotBlank(message = "Value must not be blank") @Size(max = 50, message = "Value must not exceed 50 characters") String> values;
}
