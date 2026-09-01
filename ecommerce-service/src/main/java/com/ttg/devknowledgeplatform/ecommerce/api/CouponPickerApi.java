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
     *
     * @param userUuid the caller's Keycloak UUID (excludes coupons already exhausted by the
     *                 caller's own per-user redemption limit)
     * @param target   which {@link CouponTarget} to browse
     * @param subtotal the caller's current cart subtotal — used only to compute each returned
     *                 coupon's own {@code eligible} flag, never persisted or validated here
     * @return {@code 200} with the matching coupons, biggest discount first
     */
    @GetMapping("/available")
    ResponseEntity<List<AvailableCouponResponse>> listAvailable(
            @CurrentUserId String userUuid,
            @RequestParam CouponTarget target,
            @RequestParam BigDecimal subtotal);
}
