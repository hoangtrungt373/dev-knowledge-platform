package com.ttg.devknowledgeplatform.ecommerce.payment;

import java.math.BigDecimal;

/**
 * The seam between this module's order lifecycle and an external payment processor — a GoF
 * <b>Adapter</b> (Structural): the rest of Epic 3 (Order Lifecycle & Inventory) talks to this
 * interface only, never to a concrete gateway SDK, so Epic 4 (Payments) can swap
 * {@link NoOpPaymentGatewayPort} for a real adapter (Stripe, Adyen, whatever is chosen) without
 * touching {@code orderstatus.PaymentHandoffService} or {@code orderstatus.OrderReconciliationJob}
 * at all — both already depend on this interface, not an implementation.
 *
 * <p>Every real gateway call in this reactor's future is the one step (per this epic's own locked
 * decisions) that genuinely leaves the local database transaction — the start of the Saga pattern
 * Epic 4's own user stories describe. That's exactly why {@link #charge} is keyed by
 * {@code idempotencyKey} rather than an amount alone: a crash between "gateway approved the
 * charge" and "order marked {@code CONFIRMED}" must be recoverable by asking the gateway "what did
 * you decide for this key" (via {@link #checkStatus}) rather than either assuming failure and
 * risking a double charge, or assuming success and risking confirming a charge that never
 * happened — see US-3.4 and {@code orderstatus.OrderReconciliationJob}.
 */
public interface PaymentGatewayPort {

    /**
     * Attempts to charge {@code amount} against {@code idempotencyKey} — a fresh attempt, called
     * once per order immediately after {@code OrderStatusHandlerRegistry.startPaymentProcessing}
     * commits (US-3.3).
     *
     * @param idempotencyKey a key unique to this order's payment attempt (this reactor uses the
     *                       order's own id — see {@code PendingOrderStatusHandler.startPaymentProcessing})
     * @param amount         the amount to charge, from the order's own snapshotted total
     * @return {@link PaymentOutcome#SUCCEEDED} or {@link PaymentOutcome#DECLINED} — a fresh charge
     *         attempt resolves synchronously for this interface's purposes; {@link PaymentOutcome#PENDING}
     *         is {@link #checkStatus}'s to return, not this method's
     */
    PaymentOutcome charge(String idempotencyKey, BigDecimal amount);

    /**
     * Re-queries the gateway's own ground truth for a previously-attempted charge, by the same
     * {@code idempotencyKey} passed to {@link #charge} — US-3.4's reconciliation job calls this for
     * any order stuck in {@code PAYMENT_PROCESSING} beyond its grace period, rather than assuming
     * an outcome.
     *
     * @return the gateway's actual decision, including {@link PaymentOutcome#PENDING} if it
     *         genuinely hasn't decided yet
     */
    PaymentOutcome checkStatus(String idempotencyKey);
}
