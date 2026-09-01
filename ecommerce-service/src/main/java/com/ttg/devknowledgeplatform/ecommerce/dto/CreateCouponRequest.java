package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Request payload to create a {@code Coupon}. Value-vs-{@code type} range checks (e.g. a
 * percentage over 100) and {@code startAt}/{@code endAt} ordering are enforced imperatively in
 * {@code CouponServiceImpl}, not declaratively here — the same "cross-field business rule uses
 * {@code Validator}, not Bean Validation" idiom this reactor's service layer already follows. */
@Data
public class CreateCouponRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    @NotNull(message = "Target is required")
    private CouponTarget target;

    @NotNull(message = "Type is required")
    private CouponType type;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Value must be greater than zero")
    private BigDecimal value;

    private boolean active = true;

    private Instant startAt;

    private Instant endAt;

    @DecimalMin(value = "0.0", message = "Minimum subtotal must not be negative")
    private BigDecimal minSubtotal;

    @Min(value = 1, message = "Max redemptions must be at least 1")
    private Integer maxRedemptions;

    @Min(value = 1, message = "Max redemptions per user must be at least 1")
    private Integer maxRedemptionsPerUser;

    /** Caps a single redemption's discount regardless of {@code value}/{@code type} — omitted
     * means no cap. See {@code Coupon}'s own Javadoc for why this is separate from {@code value}. */
    @DecimalMin(value = "0.0", inclusive = false, message = "Max discount amount must be greater than zero")
    private BigDecimal maxDiscountAmount;

    /** Shopper-facing summary for the future coupon picker dialog — purely presentational,
     * optional, never validated against the coupon's own actual conditions. */
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    /** Permanent, unsigned promo banner/icon URL — normally the {@code url} returned by
     * {@code CouponImageApi#upload}, set here as a plain string rather than re-uploaded on every
     * create/update. Optional. */
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;
}
