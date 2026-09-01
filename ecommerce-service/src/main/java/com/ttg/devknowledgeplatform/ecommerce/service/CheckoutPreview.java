package com.ttg.devknowledgeplatform.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The reviewable state of a checkout attempt (US-2.6) before the shopper confirms — every current
 * cart line (some possibly flagged {@code available = false} per US-2.7's revalidation), plus the
 * totals a confirm right now would produce.
 *
 * @param subtotalDiscountAmount the amount a {@code subtotalCouponCode} (Phase 2 of the Coupon
 *                               feature) deducts from {@code subtotal} — zero when no such coupon
 *                               was given or eligible; {@code subtotal} itself is never reduced,
 *                               same "raw sum of line totals" meaning it always had
 * @param originalShippingFee   what {@code shippingFee} would be absent any promotional waiver
 *                              (the automatic pricing strategy's own waiver, a {@code
 *                              shippingCouponCode}'s waiver, or both — see
 *                              {@code shipping.ShippingFeeQuote}'s own Javadoc) — equal to
 *                              {@code shippingFee} whenever nothing was waived; lets the GUI show
 *                              "was $5.00, now free" messaging instead of just the final number
 */
public record CheckoutPreview(
        List<CartLine> lines, BigDecimal subtotal, BigDecimal subtotalDiscountAmount,
        BigDecimal shippingFee, BigDecimal originalShippingFee, BigDecimal total) {
}
