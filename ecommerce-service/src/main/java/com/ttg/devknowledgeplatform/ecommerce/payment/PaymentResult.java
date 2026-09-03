package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

/**
 * The full result of a {@link PaymentGatewayPort#charge}/{@link PaymentGatewayPort#checkStatus}
 * call (US-4.1) — a GoF <b>Adapter</b>-shaped return value: every concrete gateway (a real
 * {@code StripePaymentGateway}, the deterministic {@code MockPaymentGateway}) translates its own
 * SDK's response/exception vocabulary into this one, so nothing above this interface ever sees a
 * gateway-specific type.
 *
 * <p>Widens Epic 3's original bare {@link PaymentOutcome} return type with the two things Epic 4
 * actually needs to persist onto {@code entity.Payment}: {@link #gatewayReference} (the real
 * gateway's own charge/PaymentIntent id — {@code null} for an outcome that never reached the
 * gateway far enough to get one) and, for a {@link PaymentOutcome#DECLINED} result,
 * {@link #failureCategory}/{@link #gatewayFailureMessage} (US-4.7's shopper-facing category, plus
 * the raw gateway string it was derived from, kept internal-only). {@link #outcome} alone is still
 * what {@code orderstatus.PaymentHandoffService#resolvePayment} branches on — this record only adds
 * detail alongside it, it doesn't replace it.
 */
public record PaymentResult(
        PaymentOutcome outcome,
        String gatewayReference,
        PaymentFailureCategory failureCategory,
        String gatewayFailureMessage) {

    public static PaymentResult succeeded(String gatewayReference) {
        return new PaymentResult(PaymentOutcome.SUCCEEDED, gatewayReference, null, null);
    }

    public static PaymentResult declined(
            String gatewayReference, PaymentFailureCategory failureCategory, String gatewayFailureMessage) {
        return new PaymentResult(PaymentOutcome.DECLINED, gatewayReference, failureCategory, gatewayFailureMessage);
    }

    public static PaymentResult pending(String gatewayReference) {
        return new PaymentResult(PaymentOutcome.PENDING, gatewayReference, null, null);
    }
}
