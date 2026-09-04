package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

/**
 * The full result of a {@link PaymentGatewayPort#charge}/{@link PaymentGatewayPort#checkStatus}
 * call (US-4.1) — a GoF <b>Adapter</b>-shaped return value: every concrete gateway (a real
 * {@code StripePaymentGateway}, the deterministic {@code MockPaymentGateway}) translates its own
 * SDK's response/exception vocabulary into this one, so nothing above this interface ever sees a
 * gateway-specific type.
 *
 * <p>Widens Epic 3's original bare {@link PaymentOutcome} return type with the things Epic 4
 * actually needs to persist onto {@code entity.Payment}/return to the caller:
 * {@link #gatewayReference} (the real gateway's own charge/PaymentIntent id — {@code null} for an
 * outcome that never reached the gateway far enough to get one), for a
 * {@link PaymentOutcome#DECLINED} result {@link #failureCategory}/{@link #gatewayFailureMessage}
 * (US-4.7's shopper-facing category, plus the raw gateway string it was derived from, kept
 * internal-only), and — for the client-side-confirmation flow (Option A: Stripe Elements) — a
 * {@link PaymentOutcome#PENDING} result's own {@link #clientSecret}: the PaymentIntent's client
 * secret, which the GUI needs to mount a `PaymentElement` and call `stripe.confirmPayment`. {@code
 * null} for {@link PaymentOutcome#SUCCEEDED}/{@link PaymentOutcome#DECLINED} (and for
 * {@code MockPaymentGateway}'s own always-synchronous verdicts, which never need one) — never
 * persisted onto {@code entity.Payment} (see that entity's own Javadoc), only ever passed straight
 * through to the one HTTP response that triggered this charge attempt. {@link #outcome} alone is
 * still what {@code orderstatus.PaymentHandoffService#resolvePayment} branches on — this record
 * only adds detail alongside it, it doesn't replace it.
 */
public record PaymentResult(
        PaymentOutcome outcome,
        String gatewayReference,
        PaymentFailureCategory failureCategory,
        String gatewayFailureMessage,
        String clientSecret) {

    public static PaymentResult succeeded(String gatewayReference) {
        return new PaymentResult(PaymentOutcome.SUCCEEDED, gatewayReference, null, null, null);
    }

    public static PaymentResult declined(
            String gatewayReference, PaymentFailureCategory failureCategory, String gatewayFailureMessage) {
        return new PaymentResult(PaymentOutcome.DECLINED, gatewayReference, failureCategory, gatewayFailureMessage, null);
    }

    public static PaymentResult pending(String gatewayReference, String clientSecret) {
        return new PaymentResult(PaymentOutcome.PENDING, gatewayReference, null, null, clientSecret);
    }
}
