package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.ecommerce.dto.AvailableCouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * HTTP contract for a shopper browsing currently-redeemable coupons at checkout — the backing
 * endpoint for {@code gui}'s coupon picker dialog. A separate resource from {@link CouponApi}
 * (admin CRUD, gated by {@code /api/v1/admin/**}): this is authenticated-shopper-facing, same
 * audience/security rule as {@link CartApi}/{@link CheckoutApi}/{@link OrderApi} (falls under
 * {@code SecurityConfig}'s default {@code anyRequest().authenticated()} rule, no new rule needed)
 * — mirrors the {@link OrderApi}/{@link AdminOrderApi} split for the identical "same underlying
 * resource, genuinely different audience" reason.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.CouponPickerController})
 * carries no HTTP annotations, matching every other {@code *Api}/{@code *Controller} split in this
 * module.
 */
@RequestMapping("/api/v1/coupons")
public interface CouponPickerApi {

    /**
     * Lists every currently-redeemable coupon for {@code target} — see
     * {@code CouponRedemptionService#listAvailable}'s own Javadoc for the exact eligibility
     * mechanics. Never itself validates/redeems anything; {@link CheckoutApi}'s own preview/confirm
     * remain the actual source of truth once a shopper picks one and it gets submitted for real.
     * Sorted by what's actually best for this order — see
     * {@code CouponPickerController#listAvailable}'s own Javadoc for exactly how.
     *
     * @param userUuid    the caller's Keycloak UUID (excludes coupons already exhausted by the
     *                    caller's own per-user redemption limit)
     * @param target      which {@link CouponTarget} to browse
     * @param subtotal    the caller's current cart subtotal — used to compute each returned
     *                    coupon's own {@code eligible} flag (always against the subtotal,
     *                    regardless of {@code target}), and, for {@code target == SUBTOTAL}, as
     *                    the base amount each coupon's real {@code discountAmount} is computed
     *                    against
     * @param shippingFee the caller's current quoted shipping fee (before any coupon — e.g. the
     *                    checkout preview's own {@code originalShippingFee}), used as the base
     *                    amount a {@code target == SHIPPING_FEE} coupon's real
     *                    {@code discountAmount} is computed against. Ignored for
     *                    {@code target == SUBTOTAL}; treated as {@code 0} if omitted for
     *                    {@code target == SHIPPING_FEE} (every coupon in that case computes to no
     *                    savings, which just falls to the back of the sort rather than erroring).
     * @return {@code 200} with the matching coupons, sorted eligible-first, then by real
     *         {@code discountAmount} descending within each group
     */
    @GetMapping("/available")
    ResponseEntity<List<AvailableCouponResponse>> listAvailable(
            @CurrentUserId String userUuid,
            @RequestParam CouponTarget target,
            @RequestParam BigDecimal subtotal,
            @RequestParam(required = false) BigDecimal shippingFee);
}
