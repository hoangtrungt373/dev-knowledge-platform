package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * REST response shape for one coupon in the shopper-facing "coupon picker" dialog
 * ({@code CouponPickerApi}) — deliberately leaner than admin's own {@link CouponResponse}: no
 * {@code id}/{@code active}/{@code startAt}/{@code maxRedemptions}/{@code maxRedemptionsPerUser},
 * since a coupon reaching this response has already been filtered to "currently redeemable" by
 * {@code CouponRedemptionService#listAvailable} — those fields would be redundant or, for the
 * redemption-limit counters, an internal implementation detail a shopper has no use for.
 *
 * <p>{@link #eligible} is the one field with no counterpart on {@link CouponResponse} at all — computed
 * per-request against the caller's own live cart subtotal (not persisted, not a {@code Coupon}
 * field), so the GUI can render "requires $X more" without duplicating that comparison itself. See
 * {@code CouponMapper#toAvailableResponse}'s own Javadoc for exactly how it's computed.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailableCouponResponse {

    private String code;
    private CouponTarget target;
    private CouponType type;
    private BigDecimal value;
    private BigDecimal minSubtotal;
    private BigDecimal maxDiscountAmount;
    private String description;
    private String imageUrl;
    /** Null when the coupon has no expiry — see {@code Coupon#getEndAt()}'s own Javadoc. */
    private Instant endAt;
    /** Whether the subtotal passed to {@code CouponPickerApi#listAvailable} meets this coupon's
     * own {@code minSubtotal} (always {@code true} when {@code minSubtotal} is null). Informational
     * only — {@code CheckoutApi}'s own preview/confirm remain the real source of truth once a
     * shopper actually applies a code. */
    private boolean eligible;
}
