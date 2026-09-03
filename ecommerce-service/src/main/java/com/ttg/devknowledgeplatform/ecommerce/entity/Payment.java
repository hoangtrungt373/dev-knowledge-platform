package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One payment attempt against an {@link Order} (Epic 4, US-4.2) — written with
 * {@link PaymentStatus#PENDING} <b>before</b> {@code payment.PaymentGatewayPort#charge} is ever
 * called, inside the same transaction as {@code orderstatus.PaymentHandoffService
 * #startPaymentProcessing}'s {@code PENDING -> PAYMENT_PROCESSING} order transition. This is
 * exactly what Epic 3's own reconciliation job (US-3.4) needs to exist for: a crash between
 * writing this row and getting a gateway response still leaves something durable to reconcile
 * from, rather than the order's own {@code idempotencyKey} column being the only trace of the
 * attempt.
 *
 * <p>Real {@code @ManyToOne} FK to {@link Order} — same reasoning as {@link CouponRedemption}'s
 * own FK: an {@code Order} row is permanent, with no delete path anywhere in this module, so
 * nothing here needs {@link OrderLine#getProductVariantId()}'s plain-column workaround.
 * {@link #idempotencyKey} is denormalized from {@link Order#getIdempotencyKey()} rather than
 * read through the association, so this row is self-sufficient for a gateway call/lookup on its
 * own (in particular, US-4.5's webhook handler can find the right {@code Payment} without also
 * loading its {@code Order}) — enforced unique, since exactly one payment attempt exists per key
 * in this module's current one-shot charge flow (see {@code PaymentRepository}'s own Javadoc for
 * why "one attempt per order" isn't itself enforced at the schema level).
 *
 * <p>{@link #gatewayReference}/{@link #failureCategory}/{@link #gatewayFailureMessage} are all
 * {@code null} until later phases populate them: {@link #gatewayReference} (a real gateway's own
 * charge/PaymentIntent id, used by US-4.5's webhook to correlate an inbound event back to this
 * row) once Phase 2 adds a real {@code PaymentGatewayPort} implementation;
 * {@link #failureCategory}/{@link #gatewayFailureMessage} once Phase 7 (US-4.7) maps a decline to
 * a small shopper-facing category — {@link #gatewayFailureMessage} is the raw gateway string this
 * category is derived from, kept for internal diagnosis only and never surfaced to a shopper
 * directly (see {@link PaymentFailureCategory}'s own Javadoc).
 */
@Entity
@Table(name = "PAYMENT", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "PAYMENT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class Payment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CUSTOMER_ORDER_ID", nullable = false)
    private Order order;

    @Column(name = "AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "IDEMPOTENCY_KEY", length = 64, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "GATEWAY_REFERENCE", length = 255)
    private String gatewayReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "FAILURE_CATEGORY", length = 30)
    private PaymentFailureCategory failureCategory;

    @Column(name = "GATEWAY_FAILURE_MESSAGE", columnDefinition = "TEXT")
    private String gatewayFailureMessage;
}
