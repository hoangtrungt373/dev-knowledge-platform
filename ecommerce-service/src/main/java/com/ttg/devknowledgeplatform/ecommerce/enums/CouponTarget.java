package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * What a {@link com.ttg.devknowledgeplatform.ecommerce.entity.Coupon} reduces — the cart's
 * {@code SUBTOTAL} or the order's {@code SHIPPING_FEE}, never both at once. A shopper may apply at
 * most one coupon per target on a single checkout (see {@code CheckoutCommands}'s own coupon
 * fields, Phase 2) — this two-value enum is exactly what makes "at most one per target" a type-level
 * guarantee rather than a runtime check over an arbitrary list.
 */
public enum CouponTarget {
    SUBTOTAL,
    SHIPPING_FEE
}
