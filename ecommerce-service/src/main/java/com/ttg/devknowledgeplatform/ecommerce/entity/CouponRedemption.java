package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * One redemption of a {@link Coupon} on one {@link Order} — the ledger
 * {@link Coupon#getMaxRedemptions()}/{@link Coupon#getMaxRedemptionsPerUser()} are enforced against
 * once checkout integration (Phase 2) writes rows here, and the audit trail for "which coupon, how
 * much" on a given order. {@link #discountAmount} is a snapshot of what was actually deducted at
 * redemption time — the coupon's own {@link Coupon#getValue()} could be edited by an admin
 * afterward, so this row, not a live recomputation, is what stays true to what really happened on
 * that order.
 *
 * <p>Real {@code @ManyToOne} FKs to both {@link Coupon} and {@link Order} — unlike
 * {@code OrderLine.productVariantId}'s deliberately-plain-column shape, neither parent here can
 * ever be hard-deleted out from under a redemption row: a {@link Coupon} still in use is rejected
 * at delete time ({@code CouponServiceImpl.delete}, {@code COUPON_IN_USE}), and {@code Order} rows
 * are permanent records with no delete path anywhere in this module.
 */
@Entity
@Table(name = "COUPON_REDEMPTION", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "COUPON_REDEMPTION_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class CouponRedemption extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COUPON_ID", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUSTOMER_ORDER_ID", nullable = false)
    private Order order;

    @Column(name = "OWNER_UUID", length = 36, nullable = false)
    private String ownerUuid;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;
}
