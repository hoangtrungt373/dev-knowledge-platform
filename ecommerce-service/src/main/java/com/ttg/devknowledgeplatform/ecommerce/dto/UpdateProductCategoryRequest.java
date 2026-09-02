package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** Request payload to rename a {@code ProductCategory}. */
@Data
public class UpdateProductCategoryRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    /** Parent category's primary key, or {@code null} for a root category. */
    private Integer parentId;

    /** This category's new, complete attribute schema, in display order — {@code null}/omitted
     * leaves the existing schema untouched; pass an empty list to clear it (see
     * {@code ProductCategoryService.update}'s own Javadoc). */
    @Valid
    private List<CategoryAttributeAssignmentRequest> attributes;
}
