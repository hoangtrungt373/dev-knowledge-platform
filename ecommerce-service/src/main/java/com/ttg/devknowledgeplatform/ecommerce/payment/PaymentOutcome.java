package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The ground-truth result of a payment attempt, as reported by a {@link PaymentGatewayPort} —
 * either a fresh charge attempt or a later reconciliation status check (US-3.4). Always carried
 * inside a {@link PaymentResult}, which adds the gateway reference/failure detail this bare enum
 * doesn't capture on its own.
 *
 * <p>{@link #PENDING} exists for {@link PaymentGatewayPort#checkStatus} even though no
 * implementation in this codebase produces it yet ({@code MockPaymentGateway}'s own synchronous
 * charge always resolves instantly): a real gateway's own "still processing" answer must be
 * distinguishable from a definitive {@link #SUCCEEDED}/{@link #DECLINED}, so the reconciliation job
 * knows to leave the order alone and check again later rather than guessing.
 */
public enum PaymentOutcome {
    SUCCEEDED,
    DECLINED,
    PENDING
}
