package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The full result of a {@link PaymentGatewayPort#refund} call (US-4.6) — mirrors
 * {@link PaymentResult}'s own "gateway reference plus an internal-only failure message" shape, just
 * for the narrower refund vocabulary ({@link RefundOutcome} instead of {@link PaymentOutcome}).
 */
public record RefundResult(RefundOutcome outcome, String gatewayReference, String failureMessage) {

    public static RefundResult succeeded(String gatewayReference) {
        return new RefundResult(RefundOutcome.SUCCEEDED, gatewayReference, null);
    }

    public static RefundResult failed(String gatewayReference, String failureMessage) {
        return new RefundResult(RefundOutcome.FAILED, gatewayReference, failureMessage);
    }
}
