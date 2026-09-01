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
 *
 * <p>{@link #maxDiscountAmount} (nullable — no cap) is a follow-up, independent of every field
 * above: a {@code PERCENTAGE} coupon's own discount is otherwise unbounded on a large-enough cart
 * (e.g. "20% off" on a $500 subtotal is a $100 discount) — this lets an admin express "20% off,
 * capped at $20" as two separate, composable numbers instead of forcing that shape into
 * {@link #value} itself. {@code CouponRedemptionServiceImpl.calculateDiscount} applies it
 * uniformly to both {@link CouponType} values (clamped after the raw percentage/fixed
 * calculation, before the existing base-amount clamp), not just {@code PERCENTAGE} — a
 * {@code FIXED_AMOUNT} coupon capped below its own {@link #value} is a valid, if unusual, choice
 * (further reducing an already-fixed discount), not a state worth rejecting.
 *
 * <p>{@link #description} (nullable, purely presentational) is a second follow-up — a
 * shopper-facing summary (e.g. "20% off orders over $100, up to $20") for the future {@code gui}
 * dialog that lets a shopper browse which coupons they can actually apply, rather than requiring
 * they already know a code. Never read by {@code CouponRedemptionService} — this field carries no
 * business meaning, unlike everything else on this entity.
 *
 * <p>{@link #imageUrl} (nullable, also purely presentational) is a third follow-up — a promo
 * banner/icon for that same future picker dialog, alongside {@link #description}. Deliberately a
 * <strong>permanent</strong>, unsigned URL ({@code CouponImageService}, backed by {@code infra}'s
 * {@code StorageService.uploadPublicImage}), not the time-limited presigned kind
 * {@code ProductImage}/an avatar uses — a {@code Coupon} has no "not-yet-published/deactivated"
 * access-control concern the way a {@code Product} does (see {@code StorageService}'s own Javadoc
 * for that distinction, and {@code ProductDescriptionImageService}'s identical reasoning for
 * {@code Product.description}'s own inline images): a coupon's whole purpose is being shown to
 * shoppers, so nothing here needs hiding once created.
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

    @Column(name = "MAX_DISCOUNT_AMOUNT", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "IMAGE_URL", length = 500)
    private String imageUrl;
}
