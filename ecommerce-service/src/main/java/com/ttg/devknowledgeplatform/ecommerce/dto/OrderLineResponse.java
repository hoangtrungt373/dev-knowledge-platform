package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** REST response shape for one line of a confirmed order. {@code lineTotal} is derived, not stored — see {@code OrderLine}'s Javadoc. */
@Data
@Builder
public class OrderLineResponse {

    private Integer variantId;
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
