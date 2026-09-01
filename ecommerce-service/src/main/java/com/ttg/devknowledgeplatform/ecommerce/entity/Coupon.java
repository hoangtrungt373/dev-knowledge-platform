package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A code-driven discount a shopper enters at checkout (the "ProductDiscount" feature, Phase 1 —
 * data model + basic admin CRUD; see {@code ecommerce-service/CLAUDE.md}'s own note for the full
 * phased plan). Named {@code Coupon}, not {@code ProductDiscount} — it isn't scoped to individual
 * products in this phase, and "coupon" is the shopper-facing vocabulary this feature actually uses
 * (code entry, not an automatic promotion — contrast with {@code shipping.FreeOverThresholdShippingFeeCalculator},
 * which stays a separate, code-free mechanism).
 *
 * <p>{@link #target} decides *what* a redemption reduces (the cart subtotal or the shipping fee —
 * never both from one coupon); {@link #type} decides *how much* ({@link CouponType#PERCENTAGE} of
 * the target amount, or a {@link CouponType#FIXED_AMOUNT}, clamped so a discount can never take its
 * target below zero). These are genuinely orthogonal choices, not a combinatorial set of strategy
 * classes — one entity with two small enums covers all four combinations without the class
 * explosion four separate {@code Coupon*Calculator} implementations would need.
 *
 * <p>{@link #code} is normalized to uppercase before persisting ({@code CouponServiceImpl}), so a
 * plain database {@code UNIQUE} constraint is correctly case-insensitive in practice — unlike
 * {@code ProductTag.name}, which preserves the admin's own casing and needs a functional
 * {@code LOWER(NAME)} index instead. Deliberately immutable after creation (no rename via
 * {@code update} — see {@code CouponCommands.Update}'s own Javadoc): a coupon code is typically
 * printed/shared externally once created, unlike a display name.
 *
 * <p>{@link #startAt}/{@link #endAt} (both nullable — an unset bound means "no lower/upper limit")
 * and {@link #minSubtotal} (nullable — no minimum) are this phase's eligibility conditions;
 * {@link #maxRedemptions}/{@link #maxRedemptionsPerUser} (both nullable — no cap) are enforced
 * against {@link CouponRedemption} rows once checkout integration (Phase 2) exists to write them.
 * Per-product/category eligibility scoping is Phase 3, not built yet — don't assume a coupon can be
 * restricted to specific products today.
 */
@Entity
@Table(name = "COUPON", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "COUPON_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class Coupon extends AbstractEntity {

    @Column(name = "CODE", length = 50, nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "TARGET", length = 20, nullable = false)
    private CouponTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 20, nullable = false)
    private CouponType type;

    @Column(name = "VALUE", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    @Column(name = "START_AT")
    private Instant startAt;

    @Column(name = "END_AT")
    private Instant endAt;

    @Column(name = "MIN_SUBTOTAL", precision = 12, scale = 2)
    private BigDecimal minSubtotal;

    @Column(name = "MAX_REDEMPTIONS")
    private Integer maxRedemptions;

    @Column(name = "MAX_REDEMPTIONS_PER_USER")
    private Integer maxRedemptionsPerUser;
}
