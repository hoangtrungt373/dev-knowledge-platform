package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * Lists every currently-redeemable coupon for {@code target} — active, within its date range,
     * and not yet exhausted (neither {@link Coupon#getMaxRedemptions()} nor
     * {@link Coupon#getMaxRedemptionsPerUser()} for {@code ownerUuid}, if set) — for the
     * shopper-facing coupon picker ({@code CouponPickerApi}). Deliberately does **not** filter on
     * {@link Coupon#getMinSubtotal()} the way {@link #resolve} does: unlike a specific
     * shopper-entered code (which either qualifies or doesn't), a *browsable list* is more useful
     * showing every currently-offered coupon with its own condition attached (so a shopper can see
     * "spend $20 more to unlock this") than silently hiding ones they don't yet qualify for — see
     * {@code CouponMapper#toAvailableResponse}'s {@code eligible} flag for how the caller's own
     * live subtotal gets surfaced instead.
     *
     * @param target    which slot to browse ({@code SUBTOTAL} or {@code SHIPPING_FEE})
     * @param ownerUuid the caller's Keycloak UUID, for the per-user redemption cap
     * @return matching coupons, biggest discount first
     */
    List<Coupon> listAvailable(CouponTarget target, String ownerUuid);

    /**
     * Like {@link #listAvailable}, but pre-computed and sorted for direct display — each result
     * also carries whether it's currently {@link RankedCoupon#eligible()} for {@code subtotal} and
     * what it would actually {@link RankedCoupon#discountAmount()} deduct from {@code baseAmount}
     * right now (via {@link #calculateDiscount}), ordered eligible-first, then by that discount
     * amount descending within each group — "what's best for this order," not a coupon's own
     * declared {@link Coupon#getValue()} (a {@code PERCENTAGE} coupon's raw value alone doesn't say
     * how much money it actually saves, especially once {@link Coupon#getMaxDiscountAmount()} caps
     * it). An ineligible coupon always sorts after every eligible one regardless of how large its
     * theoretical discount is — it can't be applied right now no matter what, so ranking it above a
     * smaller-but-usable coupon would be actively misleading.
     *
     * <p>{@code CouponPickerApi} is the one caller — {@code CouponPickerController} stays a thin
     * pass-through, mapping each {@link RankedCoupon} straight to an
     * {@code AvailableCouponResponse}, per this reactor's own "business logic belongs in the
     * service layer, not the controller" convention.
     *
     * @param target     which slot to browse ({@code SUBTOTAL} or {@code SHIPPING_FEE})
     * @param ownerUuid  the caller's Keycloak UUID, for the per-user redemption cap
     * @param subtotal   the caller's current cart subtotal — for the {@code eligible} flag, and,
     *                   when {@code target == SUBTOTAL}, as {@code baseAmount} too
     * @param baseAmount the amount each coupon's discount is computed against — the cart subtotal
     *                   for {@code target == SUBTOTAL}, the caller's current quoted shipping fee
     *                   for {@code target == SHIPPING_FEE} (the caller resolves which one to pass,
     *                   the same target-based choice {@code CheckoutServiceImpl.resolveDiscounts}
     *                   already makes for the real checkout path)
     * @return matching coupons, sorted eligible-first then by real discount amount descending
     */
    List<RankedCoupon> listAvailableRanked(CouponTarget target, String ownerUuid, BigDecimal subtotal, BigDecimal baseAmount);

    /** One {@link #listAvailableRanked} result — a {@link Coupon} plus the two values a display
     * needs that aren't {@code Coupon} fields themselves (see that method's own Javadoc). */
    record RankedCoupon(Coupon coupon, boolean eligible, BigDecimal discountAmount) {
    }
}
