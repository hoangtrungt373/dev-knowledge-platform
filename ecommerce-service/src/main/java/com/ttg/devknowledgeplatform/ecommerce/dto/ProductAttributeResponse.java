package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** REST response shape for {@code ProductAttribute}, its {@code values} in display order. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductAttributeResponse {

    private Integer id;
    private String name;
    private List<ProductAttributeValueResponse> values;
    private Instant createdAt;
    private Instant updatedAt;
}
