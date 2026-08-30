package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/** REST response shape for one line in a {@code CartResponse}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartLineResponse {

    private Integer variantId;
    private Integer quantity;
    /** {@code false} when the variant (or its product) no longer exists/is active — US-2.7. Fields below are {@code null} in that case except {@link #variantId}/{@link #quantity}. */
    private boolean available;
    private String sku;
    private Integer productId;
    private String productName;
    private String productSlug;
    private Map<String, String> attributes;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    /** Time-limited presigned GET URL for the product's first gallery image (by sortOrder); null if the product has no images yet. */
    private String primaryImageUrl;
    /** Units currently available to buy for this line's variant ({@code stockQuantity - reservedQuantity}) — null when {@link #available} is false, same as every field above it. Lets the GUI cap a quantity picker and show a low-stock nudge without a second round trip to the product detail endpoint. */
    private Integer availableQuantity;
}
