package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * Lifecycle status of a {@code Payment} row (Epic 4, US-4.2), per
 * {@code docs/user-stories/04-payments.md}:
 *
 * <pre>
 *   —          → PENDING     (row written, before the gateway is ever called — US-4.2)
 *   PENDING    → SUCCEEDED   (gateway confirmed the charge synchronously, or a later webhook did — US-4.3/4.5)
 *   PENDING    → DECLINED    (gateway declined the charge synchronously, or a later webhook did — US-4.3/4.5)
 *   PENDING    → CANCELLED   (shopper cancels while the charge is still an unconfirmed PaymentIntent)
 *   SUCCEEDED  → REFUNDED    (a CONFIRMED order is cancelled — US-4.6, full refund only)
 * </pre>
 *
 * <p>{@link #PENDING}/{@link #SUCCEEDED}/{@link #DECLINED} deliberately mirror
 * {@code payment.PaymentOutcome}'s own vocabulary (Epic 3's gateway seam) but are <b>not</b> the
 * same enum: {@code PaymentOutcome.PENDING} means "the gateway itself hasn't decided yet" (only
 * {@code checkStatus} ever returns it — no charge attempt in this codebase produces it
 * synchronously), while this {@link #PENDING} means "the row exists, the gateway hasn't been
 * called at all yet" — the brief window US-4.2 exists to make crash-safe (Option A's
 * client-side-confirmation flow widens that window considerably in practice, but the row's own
 * {@code status} still legitimately reads {@code PENDING} throughout it — see {@code
 * orderstatus.PaymentHandoffService#resolvePayment}'s own Javadoc). {@link #REFUNDED} has no
 * {@code PaymentOutcome} counterpart at all, since a refund is a second, later gateway operation
 * ({@code PaymentGatewayPort#refund}, US-4.6), not a charge outcome. {@link #CANCELLED} likewise has
 * no {@code PaymentOutcome} counterpart — it comes from {@code payment.PaymentGatewayPort
 * #cancelUnconfirmed}'s own dedicated {@code payment.CancellationOutcome} vocabulary, and is
 * deliberately distinct from {@link #DECLINED}: nothing was actually declined by the gateway when a
 * shopper simply chooses not to pay, and reusing {@link #DECLINED} here would show a misleading
 * "payment declined" reason on an order the shopper themselves cancelled.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    DECLINED,
    REFUNDED,
    CANCELLED
}
