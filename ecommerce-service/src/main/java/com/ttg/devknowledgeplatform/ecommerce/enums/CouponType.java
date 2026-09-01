package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * How a {@link com.ttg.devknowledgeplatform.ecommerce.entity.Coupon}'s {@code value} is interpreted
 * — {@code PERCENTAGE} (0 exclusive to 100 inclusive, applied against whatever amount its
 * {@link CouponTarget} names) or {@code FIXED_AMOUNT} (a flat currency amount, clamped so a
 * discount can never take the target below zero — see {@code CouponServiceImpl}'s own validation
 * and the Phase 2 discount-calculation note in {@code ecommerce-service/CLAUDE.md}).
 */
public enum CouponType {
    PERCENTAGE,
    FIXED_AMOUNT
}
