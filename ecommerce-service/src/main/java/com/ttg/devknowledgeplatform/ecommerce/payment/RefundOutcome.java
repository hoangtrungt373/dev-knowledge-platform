package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The ground-truth result of a {@link PaymentGatewayPort#refund} call (US-4.6) — deliberately its
 * own enum, not a reuse of {@link PaymentOutcome}: a refund is a second, later gateway operation
 * against an already-{@code SUCCEEDED} payment, not a charge outcome, and {@link PaymentOutcome
 * #DECLINED} would misname what is really a refund <i>failure</i> (e.g. the funds were already
 * returned by some other means).
 *
 * <p>{@link #PENDING} exists for the same forward-looking reason {@link PaymentOutcome#PENDING}
 * does — some gateways settle a refund asynchronously — even though no implementation in this
 * codebase produces it yet (both {@code MockPaymentGateway} and {@code StripePaymentGateway}'s own
 * synchronous refund call resolve immediately in every case this reactor exercises).
 */
public enum RefundOutcome {
    SUCCEEDED,
    FAILED,
    PENDING
}
