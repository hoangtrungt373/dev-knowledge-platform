package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** REST response shape for {@code Coupon}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouponResponse {

    private Integer id;
    private String code;
    private CouponTarget target;
    private CouponType type;
    private BigDecimal value;
    private Boolean active;
    private Instant startAt;
    private Instant endAt;
    private BigDecimal minSubtotal;
    private Integer maxRedemptions;
    private Integer maxRedemptionsPerUser;
    /** Caps a single redemption's discount regardless of {@code value}/{@code type} — null means
     * no cap. See {@code Coupon}'s own Javadoc for why this is a separate field, not folded into
     * {@code value}. */
    private BigDecimal maxDiscountAmount;
    /** Shopper-facing summary (e.g. "20% off orders over $100, up to $20") for the future coupon
     * picker dialog — purely presentational, never used in eligibility/discount calculation. */
    private String description;
    /** Permanent, unsigned promo banner/icon URL for that same future dialog — null if the admin
     * never uploaded one. See {@code Coupon}'s own Javadoc for why this is a permanent URL, not a
     * presigned one. */
    private String imageUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
