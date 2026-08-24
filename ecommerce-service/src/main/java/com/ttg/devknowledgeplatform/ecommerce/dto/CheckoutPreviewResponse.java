package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** REST response shape for {@code GET /api/v1/checkout/preview} (US-2.6). */
@Data
@Builder
public class CheckoutPreviewResponse {

    /** Every current cart line — some may have {@code available = false} (US-2.7). */
    private List<CartLineResponse> lines;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal total;
}
