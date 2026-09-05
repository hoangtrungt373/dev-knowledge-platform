package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The full result of a {@link PaymentGatewayPort#cancelUnconfirmed} call — mirrors
 * {@link RefundResult}'s own "narrow, dedicated vocabulary" shape rather than reusing
 * {@link PaymentResult}: {@link CancellationOutcome#CANCELLED} means the gateway genuinely voided
 * the not-yet-confirmed charge attempt at the shopper's own request — {@code
 * orderstatus.PaymentHandoffService#applyGatewayCancellation} marks the {@code Payment} row {@code
 * CANCELLED}, never {@code DECLINED}, since nothing was actually declined and mislabeling it a
 * decline would show a misleading "payment declined" reason on an order the shopper themselves
 * cancelled (see {@code enums.PaymentStatus}'s own Javadoc). {@link
 * CancellationOutcome#ALREADY_RESOLVED} covers the real race this operation must handle: the
 * shopper's own browser confirmed the charge (or it was otherwise already declined) a moment before
 * this cancel call reached the gateway — {@link #resolvedResult} then carries the gateway's actual,
 * already-terminal verdict, and the caller must route it through the ordinary {@code
 * PaymentHandoffService#resolvePayment} path instead of forcing a cancellation that never really
 * happened.
 */
public record PaymentCancellationResult(CancellationOutcome outcome, PaymentResult resolvedResult) {

    public static PaymentCancellationResult cancelled() {
        return new PaymentCancellationResult(CancellationOutcome.CANCELLED, null);
    }

    public static PaymentCancellationResult alreadyResolved(PaymentResult resolvedResult) {
        return new PaymentCancellationResult(CancellationOutcome.ALREADY_RESOLVED, resolvedResult);
    }
}
