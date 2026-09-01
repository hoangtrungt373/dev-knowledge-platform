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
    /** What {@code shippingFee} would be absent any promotional waiver — equal to
     * {@code shippingFee} whenever nothing was waived; e.g. {@code $5.00} here alongside a
     * {@code shippingFee} of {@code $0.00} means free shipping just kicked in. */
    private BigDecimal originalShippingFee;
    private BigDecimal total;
}
