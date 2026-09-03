package com.ttg.devknowledgeplatform.ecommerce.enums;

import lombok.Getter;

/**
 * A small, shopper-facing category a {@code Payment}'s decline is mapped to (US-4.7) — never the
 * raw gateway decline code/message itself, which stays internal-only on
 * {@code Payment#getGatewayFailureMessage()}.
 *
 * <p>The Stripe-decline-code → category mapping itself was built in Phase 2
 * ({@code payment.StripeFailureCategoryMapper}) — squarely the Adapter's own job of translating
 * Stripe's vocabulary into this codebase's, so it landed there rather than waiting for this phase.
 * Phase 7 (US-4.7) is what actually needed a real "clear but non-technical reason" per category —
 * {@link #shopperMessage}, a constructor-supplied field mirroring {@code common.exception.ErrorCode}'s
 * own {@code getMessage()} convention (server-owned copy, not left for a client to invent), exposed
 * via {@code dto.OrderResponse#getPaymentFailureMessage()} — never
 * {@code Payment#getGatewayFailureMessage()} itself, which a shopper should never see verbatim.
 */
@Getter
public enum PaymentFailureCategory {
    INSUFFICIENT_FUNDS("Your card doesn't have enough funds for this purchase. Please try a "
            + "different payment method or add funds and try again."),
    CARD_DECLINED("Your card was declined. Please check your card details or try a different "
            + "payment method."),
    GATEWAY_ERROR("We couldn't process your payment due to a temporary issue. Please try again "
            + "in a moment, or contact support if this continues.");

    private final String shopperMessage;

    PaymentFailureCategory(String shopperMessage) {
        this.shopperMessage = shopperMessage;
    }
}
