package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Request payload to update a {@code Coupon} — no {@code code} field, since a coupon's code is
 * immutable after creation (see {@code Coupon}'s own Javadoc). */
@Data
public class UpdateCouponRequest {

    @NotNull(message = "Target is required")
    private CouponTarget target;

    @NotNull(message = "Type is required")
    private CouponType type;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Value must be greater than zero")
    private BigDecimal value;

    private boolean active;

    private Instant startAt;

    private Instant endAt;

    @DecimalMin(value = "0.0", message = "Minimum subtotal must not be negative")
    private BigDecimal minSubtotal;

    @Min(value = 1, message = "Max redemptions must be at least 1")
    private Integer maxRedemptions;

    @Min(value = 1, message = "Max redemptions per user must be at least 1")
    private Integer maxRedemptionsPerUser;
}
