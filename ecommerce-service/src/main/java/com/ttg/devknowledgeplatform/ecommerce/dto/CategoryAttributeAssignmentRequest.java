package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** One requested {@code ProductCategory} → {@code ProductAttribute} assignment — no
 * {@code displayOrder} field; an assignment's order is its position in the submitted list. */
@Data
public class CategoryAttributeAssignmentRequest {

    @NotNull(message = "attributeId is required")
    private Integer attributeId;

    private boolean required;
}
