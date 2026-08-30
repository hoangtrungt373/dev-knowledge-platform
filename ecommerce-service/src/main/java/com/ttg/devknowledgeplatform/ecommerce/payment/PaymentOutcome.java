package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The ground-truth result of a payment attempt, as reported by a {@link PaymentGatewayPort} —
 * either a fresh charge attempt or a later reconciliation status check (US-3.4).
 *
 * <p>{@link #PENDING} exists for {@link PaymentGatewayPort#checkStatus} even though no
 * implementation in this codebase produces it yet ({@link NoOpPaymentGatewayPort} always resolves
 * instantly): a real gateway's own "still processing" answer must be distinguishable from a
 * definitive {@link #SUCCEEDED}/{@link #DECLINED}, so the reconciliation job knows to leave the
 * order alone and check again later rather than guessing.
 */
public enum PaymentOutcome {
    SUCCEEDED,
    DECLINED,
    PENDING
}
