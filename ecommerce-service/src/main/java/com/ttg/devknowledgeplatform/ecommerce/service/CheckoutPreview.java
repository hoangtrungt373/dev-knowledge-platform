package com.ttg.devknowledgeplatform.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The reviewable state of a checkout attempt (US-2.6) before the shopper confirms — every current
 * cart line (some possibly flagged {@code available = false} per US-2.7's revalidation), plus the
 * totals a confirm right now would produce.
 */
public record CheckoutPreview(List<CartLine> lines, BigDecimal subtotal, BigDecimal shippingFee, BigDecimal total) {
}
