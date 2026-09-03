package com.ttg.devknowledgeplatform.ecommerce.payment;

/**
 * Thrown by a {@link PaymentGatewayPort} implementation for a genuine gateway/network/API failure —
 * never for a card decline, which is a definitive business answer represented as
 * {@link PaymentResult#declined}/{@link RefundResult#failed} instead, not an exception.
 *
 * <p>The distinction matters operationally: {@code service.impl.OrderServiceImpl#initiatePayment}
 * is deliberately not itself transactional, specifically so that if {@link PaymentGatewayPort
 * #charge} throws (this exception, in practice), the order is left durably {@code
 * PAYMENT_PROCESSING} — already committed by {@code orderstatus.PaymentHandoffService
 * #startPaymentProcessing} — for {@code orderstatus.OrderReconciliationJob} (US-3.4) to resolve
 * later, rather than the caller having to guess whether a thrown exception means "the charge
 * definitely failed" (it doesn't — it means "we don't actually know yet").
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
