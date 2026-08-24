package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** REST response shape for a shopper's cart (US-2.3). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartResponse {

    private List<CartLineResponse> lines;
    /** Sum of {@code lineTotal} across {@code available} lines only. */
    private BigDecimal subtotal;
    /** Sum of {@code quantity} across {@code available} lines only. */
    private int itemCount;
}
