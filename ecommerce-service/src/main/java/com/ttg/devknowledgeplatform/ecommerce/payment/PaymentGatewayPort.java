package com.ttg.devknowledgeplatform.ecommerce.payment;

import java.math.BigDecimal;

/**
 * The seam between this module's order lifecycle and an external payment processor — a GoF
 * <b>Adapter</b> (Structural): the rest of Epic 3 (Order Lifecycle & Inventory) talks to this
 * interface only, never to a concrete gateway SDK. {@code MockPaymentGateway} and
 * {@code StripePaymentGateway} are a GoF <b>Strategy</b> (Behavioral) pair on top of that same
 * Adapter role — selected via {@code app.ecommerce.payment.gateway} (default {@code mock}), so
 * neither {@code orderstatus.PaymentHandoffService} nor {@code orderstatus.OrderReconciliationJob}
 * needs to know or care which one is active.
 *
 * <p>Every real gateway call in this reactor is the one step (per Epic 4's own locked decisions)
 * that genuinely leaves the local database transaction — the start of the Saga pattern Epic 4's
 * own user stories describe. That's exactly why {@link #charge} is keyed by
 * {@code idempotencyKey} rather than an amount alone: a crash between "gateway approved the
 * charge" and "order marked {@code CONFIRMED}" must be recoverable by asking the gateway "what did
 * you decide for this key" (via {@link #checkStatus}) rather than either assuming failure and
 * risking a double charge, or assuming success and risking confirming a charge that never
 * happened — see US-3.4 and {@code orderstatus.OrderReconciliationJob}.
 *
 * <p>{@link #refund} is deliberately keyed by {@code gatewayReference} (the gateway's own
 * charge/PaymentIntent id, as captured on {@code entity.Payment#getGatewayReference()}), not this
 * module's internal {@code paymentId} — the gateway itself has no notion of our numeric primary
 * key, only of whatever id it handed back from the original {@link #charge}. It's still full-refund
 * only (US-4.6, no partial-order-line cancellation is modeled) — {@code amount} is always the
 * payment's own original total, never a partial figure.
 *
 * <p>A genuine gateway/network/API failure (as opposed to a definitive card decline) is signaled by
 * throwing {@link PaymentGatewayException}, never by returning a {@code DECLINED}/{@code FAILED}
 * result — see that exception's own Javadoc for why the distinction matters.
 *
 * <p>{@link #cancelUnconfirmed} (an Option A follow-up) closes a real gap the client-side-
 * confirmation flow opened: a shopper who explicitly cancels an order while its charge attempt is
 * still an unconfirmed Stripe PaymentIntent leaves nothing that will ever resolve it on its own — no
 * webhook is coming (the shopper never confirmed), and a reconciliation poll just keeps re-reporting
 * {@link PaymentOutcome#PENDING} forever. This method actively voids the charge attempt at the
 * gateway instead of waiting on an outcome that will never arrive — see
 * {@code orderstatus.PaymentHandoffService#applyCancellation}'s own Javadoc for the full incident.
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
     * @return a {@link PaymentResult} carrying {@link PaymentOutcome#SUCCEEDED} or
     *         {@link PaymentOutcome#DECLINED} — a fresh charge attempt resolves synchronously for
     *         this interface's purposes; {@link PaymentOutcome#PENDING} is {@link #checkStatus}'s
     *         to return, not this method's
     * @throws PaymentGatewayException if the gateway call itself failed (network/API error) rather
     *         than definitively declining the charge
     */
    PaymentResult charge(String idempotencyKey, BigDecimal amount);

    /**
     * Re-queries the gateway's own ground truth for a previously-attempted charge, by the same
     * {@code idempotencyKey} passed to {@link #charge} — US-3.4's reconciliation job calls this for
     * any order stuck in {@code PAYMENT_PROCESSING} beyond its grace period, rather than assuming
     * an outcome.
     *
     * @return the gateway's actual decision, including {@link PaymentOutcome#PENDING} if it
     *         genuinely hasn't decided yet
     * @throws PaymentGatewayException if the gateway call itself failed (network/API error)
     */
    PaymentResult checkStatus(String idempotencyKey);

    /**
     * Issues a full refund (US-4.6, no partial refunds) against a previously-succeeded charge.
     *
     * @param gatewayReference the gateway's own id for the original charge (from that charge's own
     *                          {@link PaymentResult#gatewayReference()})
     * @param amount            the full amount to refund — always the payment's own original total
     * @throws PaymentGatewayException if the gateway call itself failed (network/API error) rather
     *         than definitively failing the refund
     */
    RefundResult refund(String gatewayReference, BigDecimal amount);

    /**
     * Cancels a not-yet-confirmed charge attempt at the gateway (Stripe: {@code
     * PaymentIntent.cancel}) — for an order whose cancel only queued because payment was still
     * unresolved, when nothing else will ever pick that queued cancel up.
     *
     * @param gatewayReference the gateway's own id for the unconfirmed charge attempt (from that
     *                          charge's own {@link PaymentResult#gatewayReference()})
     * @return the gateway's own definitive verdict — see {@link PaymentCancellationResult}'s own
     *         Javadoc for the two possible outcomes
     * @throws PaymentGatewayException if the gateway call itself failed (network/API error) rather
     *         than definitively resolving one way or the other
     */
    PaymentCancellationResult cancelUnconfirmed(String gatewayReference);
}
