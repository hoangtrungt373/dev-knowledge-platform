package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** REST response shape for a browse/search result row (from {@code ProductSearchView}). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSearchResponse {

    private Integer productId;
    private String name;
    private String slug;
    private Integer productCategoryId;
    private String categoryName;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private boolean inStock;
    /** The product's first gallery image by sort order (US-1.1); {@code null} if it has none yet. */
    private String primaryImageStorageKey;
    private Map<String, List<String>> availableAttributes;
}
