package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link Payment}. Plain {@link JpaRepository} — this module's current one-shot
 * charge flow (Epic 3's {@code orderstatus.PaymentHandoffService}) stamps exactly one
 * {@code idempotencyKey} per order and never retries a declined charge, so
 * {@link #findByOrderId} returning a single row is correct for every caller today; nothing here
 * enforces that at the schema level (no {@code UNIQUE(CUSTOMER_ORDER_ID)}), since a future retry
 * flow legitimately would need more than one {@link Payment} row per order — widen this to a list
 * query first if that's ever built, rather than assuming this method stays valid.
 */
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    /**
     * The payment attempt for a given order, if one has been started yet (US-4.2's row is written
     * before the gateway is ever called, so "no row" and "not yet attempted" are the same state).
     */
    Optional<Payment> findByOrderId(Integer orderId);

    /**
     * Looks a payment attempt up by its own denormalized {@code idempotencyKey} — the same key
     * passed to {@code payment.PaymentGatewayPort#charge} as Stripe's native {@code Idempotency-Key}
     * header (US-4.1). Not how the webhook correlates an inbound event back to a row — see
     * {@link #findByGatewayReference} for that.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Looks a payment attempt up by the real gateway's own charge/PaymentIntent id — how
     * {@code webhook.StripeWebhookService} (US-4.5) correlates an inbound Stripe event back to this
     * row, since a webhook payload only ever names a PaymentIntent id, never this reactor's own
     * {@code orderId}/{@code paymentId}. Backed by a partial unique index
     * ({@code UX_PAYMENT_GATEWAY_REFERENCE}, {@code WHERE GATEWAY_REFERENCE IS NOT NULL}) — the
     * column is nullable (unpopulated during the brief {@code PENDING} window before any gateway
     * response exists), but a webhook always names a real one.
     */
    Optional<Payment> findByGatewayReference(String gatewayReference);
}
