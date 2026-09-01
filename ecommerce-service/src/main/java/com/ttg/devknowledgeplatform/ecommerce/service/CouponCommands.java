package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain input records for {@link CouponService}, mirroring {@code api}'s
 * {@code CreateCouponRequest}/{@code UpdateCouponRequest} field-for-field but without any
 * REST/validation-annotation concerns — same pattern as {@link SavedAddressCommands}/
 * {@link ProductCommands}.
 */
public final class CouponCommands {

    private CouponCommands() {}

    public record Create(
            String code, CouponTarget target, CouponType type, BigDecimal value, boolean active,
            Instant startAt, Instant endAt, BigDecimal minSubtotal,
            Integer maxRedemptions, Integer maxRedemptionsPerUser) {
    }

    /** No {@code code} field — a coupon's code is immutable after creation (see {@code Coupon}'s
     * own Javadoc for why). */
    public record Update(
            CouponTarget target, CouponType type, BigDecimal value, boolean active,
            Instant startAt, Instant endAt, BigDecimal minSubtotal,
            Integer maxRedemptions, Integer maxRedemptionsPerUser) {
    }
}
