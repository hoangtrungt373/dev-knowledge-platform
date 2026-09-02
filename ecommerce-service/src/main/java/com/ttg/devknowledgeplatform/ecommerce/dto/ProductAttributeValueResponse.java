package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

/** REST response shape for one {@code ProductAttributeValue}. */
@Data
@Builder
public class ProductAttributeValueResponse {

    private Integer id;
    private String value;
    private Integer displayOrder;
}
