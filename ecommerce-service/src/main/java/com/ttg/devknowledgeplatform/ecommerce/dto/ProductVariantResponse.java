package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** REST response shape for {@code ProductVariant}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductVariantResponse {

    private Integer id;
    private String sku;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer reservedQuantity;
    private Map<String, String> attributes;
}
