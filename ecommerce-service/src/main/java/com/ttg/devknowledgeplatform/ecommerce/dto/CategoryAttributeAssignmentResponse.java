package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

/** REST response shape for one {@code ProductCategoryAttribute} assignment — ids only, not the
 * full attribute (matches {@code ProductResponse.tagIds}'s own "ids only" precedent); a future
 * admin GUI cross-references against its own {@code GET /api/v1/admin/product-attributes} list. */
@Data
@Builder
public class CategoryAttributeAssignmentResponse {

    private Integer attributeId;
    private boolean required;
    private Integer displayOrder;
}
