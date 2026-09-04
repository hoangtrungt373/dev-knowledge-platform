package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * The ground-truth result of a {@link PaymentGatewayPort#cancelUnconfirmed} call — deliberately its
 * own enum, not a reuse of {@link PaymentOutcome}: voiding a not-yet-confirmed charge attempt that
 * the shopper themselves chose to abandon is a different event from the gateway declining a charge,
 * and {@link PaymentOutcome#DECLINED} would mislabel a shopper-initiated cancel as a card decline
 * (see {@link PaymentCancellationResult}'s own Javadoc for the full reasoning).
 */
public enum CancellationOutcome {
    /** The gateway genuinely voided the not-yet-confirmed charge attempt. */
    CANCELLED,
    /** The charge had already reached a real terminal state before this call arrived. */
    ALREADY_RESOLVED
}
