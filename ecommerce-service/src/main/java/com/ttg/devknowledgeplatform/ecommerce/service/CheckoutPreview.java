package com.ttg.devknowledgeplatform.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The reviewable state of a checkout attempt (US-2.6) before the shopper confirms — every current
 * cart line (some possibly flagged {@code available = false} per US-2.7's revalidation), plus the
 * totals a confirm right now would produce.
 *
 * @param originalShippingFee what {@code shippingFee} would be absent any promotional waiver (see
 *                            {@code shipping.ShippingFeeQuote}'s own Javadoc) — equal to
 *                            {@code shippingFee} whenever nothing was waived; lets the GUI show
 *                            "was $5.00, now free" messaging instead of just the final number
 */
public record CheckoutPreview(
        List<CartLine> lines, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal originalShippingFee, BigDecimal total) {
}
