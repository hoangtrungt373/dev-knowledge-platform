package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import java.math.BigDecimal;

/**
 * Phase 2 of the "ProductDiscount"/Coupon feature — validating a shopper-entered code against a
 * cart and, once an order exists, recording that it was actually redeemed. Deliberately a separate
 * service from {@link CouponService} (pure admin CRUD) — same split {@code content-service}'s own
 * {@code TagService}/{@code QuestionAnswerServiceImpl.applyTagIds} establishes between owning an
 * entity's lifecycle and applying it elsewhere.
 *
 * <p>{@link #resolve} and {@link #calculateDiscount} are deliberately two separate steps, not one
 * combined call: the amount a coupon's discount is computed *against* differs by
 * {@link CouponTarget} (the cart subtotal for {@code SUBTOTAL}, the shipping fee for
 * {@code SHIPPING_FEE}), but {@link Coupon#getMinSubtotal()} is always checked against the cart's
 * own subtotal regardless of target (a free-shipping code can still require "$30 minimum order"
 * even though it discounts shipping, not the subtotal itself) — {@code resolve} owns that
 * eligibility check and has no need to know which amount the discount will actually apply to,
 * while {@code calculateDiscount} owns the arithmetic and has no need to know why the coupon was
 * eligible in the first place.
 */
public interface CouponRedemptionService {

    /**
     * Validates {@code code} for {@code target} — exists, active, correct target, within its date
     * range, the cart's {@code subtotal} meets {@link Coupon#getMinSubtotal()} (if set), and
     * neither {@link Coupon#getMaxRedemptions()} nor {@link Coupon#getMaxRedemptionsPerUser()} (if
     * set) has already been reached. Does not record a redemption — see {@link #redeem} for that;
     * this method alone is safe to call from a read-only preview.
     *
     * @param code      the shopper-entered code (case-insensitive — normalized the same way
     *                  {@code CouponServiceImpl} normalizes on create)
     * @param target    which slot this code was submitted for ({@code CheckoutServiceImpl}'s own
     *                  {@code subtotalCouponCode}/{@code shippingCouponCode} parameters)
     * @param ownerUuid the caller's Keycloak UUID, for the per-user redemption cap
     * @param subtotal  the cart's current subtotal, for the {@code minSubtotal} condition
     * @return the resolved, currently-eligible coupon
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException with a {@code COUPON_*}
     *         code on any ineligibility
     */
    Coupon resolve(String code, CouponTarget target, String ownerUuid, BigDecimal subtotal);

    /**
     * Computes what {@code coupon} deducts from {@code baseAmount} — {@code baseAmount} times
     * {@link Coupon#getValue()}{@code / 100} for {@link com.ttg.devknowledgeplatform.ecommerce.enums.CouponType#PERCENTAGE},
     * or {@link Coupon#getValue()} itself for {@code FIXED_AMOUNT} — clamped so the discount can
     * never exceed {@code baseAmount} (a discount can never take its target below zero) or be
     * negative.
     */
    BigDecimal calculateDiscount(Coupon coupon, BigDecimal baseAmount);

    /**
     * Persists a {@link com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption} row for
     * {@code coupon} against {@code order} — call only once {@code order} has already been
     * durably saved (never during {@code preview}), so the redemption-count checks in a later call
     * to {@link #resolve} see it.
     *
     * @param discountAmount the actual amount deducted — a snapshot, since {@code coupon}'s own
     *                       {@code value} could be edited by an admin afterward
     */
    void redeem(Coupon coupon, Order order, String ownerUuid, BigDecimal discountAmount);
}
