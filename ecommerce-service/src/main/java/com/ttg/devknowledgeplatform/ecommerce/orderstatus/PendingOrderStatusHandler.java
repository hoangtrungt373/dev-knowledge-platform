package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link OrderStatus#PENDING}'s transitions: the reservation-expiry timeout (US-3.2), a shopper's
 * own cancel before payment is ever attempted (US-3.6), and starting the payment handoff (US-3.3).
 * {@link #expire}/{@link #cancel} both release every line's reservation — nothing was ever sold
 * from a {@code PENDING} order, so there's only a reservation to give back, never stock to restock
 * (see {@link OrderStatusTransitions#releaseReservations}).
 */
@Component
@RequiredArgsConstructor
public class PendingOrderStatusHandler implements OrderStatusHandler {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public OrderStatus status() {
        return OrderStatus.PENDING;
    }

    @Override
    public void expire(Order order) {
        OrderStatusTransitions.releaseReservations(order, productVariantRepository);
        OrderStatusTransitions.transitionTo(order, OrderStatus.EXPIRED,
                "Reservation expired before payment was attempted");
    }

    @Override
    public void cancel(Order order) {
        OrderStatusTransitions.releaseReservations(order, productVariantRepository);
        OrderStatusTransitions.transitionTo(order, OrderStatus.CANCELLED,
                "Cancelled by shopper before payment");
    }

    /**
     * US-3.3: stamps a fresh idempotency key and the reconciliation clock, immediately before
     * whatever calls this hands off to the payment gateway. No inventory action — the reservation
     * stands unchanged until the gateway actually answers (see
     * {@code PaymentProcessingOrderStatusHandler#confirmPayment}/{@code #failPayment}).
     *
     * <p><b>Bug fix: a random {@link UUID}, not the order's own id.</b> This originally stamped
     * {@code String.valueOf(order.getId())} ("a reasonable default" per this epic's own original
     * locked decisions) — harmless until a local dev database reset (e.g. {@code
     * purge-seed-data.sql}'s {@code TRUNCATE ... RESTART IDENTITY}) recycles {@code
     * CUSTOMER_ORDER_SEQ} back to a previously-used value. Stripe's own idempotency cache is kept
     * server-side for 24 hours, entirely independent of anything in this app's own database — a new,
     * unrelated order that happens to land on a previously-used id (a different cart, a different
     * total) would send the *same* idempotency key string with a *different* amount, and Stripe
     * correctly refuses that as an ambiguous retry (a real {@code
     * com.stripe.exception.IdempotencyException}: "keys for idempotent requests can only be used
     * with the same parameters they were first used with"). A random UUID can never collide across a
     * database reset the way a recycled primary key can — this is a real, {@code
     * order.getId()}-shaped bug this class had, not a hypothetical. This does not change the
     * re-entrancy guarantee itself, which lives entirely in {@code
     * orderstatus.PaymentHandoffService#startPaymentProcessing}'s own status check (this method is
     * never reached a second time for an order already {@code PAYMENT_PROCESSING}, so the key is
     * still stamped exactly once per order regardless of its value's shape).
     */
    @Override
    public void startPaymentProcessing(Order order) {
        order.setIdempotencyKey(UUID.randomUUID().toString());
        order.setPaymentProcessingStartedAt(Instant.now());
        OrderStatusTransitions.transitionTo(order, OrderStatus.PAYMENT_PROCESSING, null);
    }
}
