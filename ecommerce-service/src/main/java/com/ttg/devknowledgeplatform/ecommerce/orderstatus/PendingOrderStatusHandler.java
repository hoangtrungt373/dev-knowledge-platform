package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;

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
     * US-3.3: stamps a fresh idempotency key (the order's own id — "a reasonable default" per
     * this epic's own locked decisions) and the reconciliation clock, immediately before whatever
     * calls this hands off to the payment gateway. No inventory action — the reservation stands
     * unchanged until the gateway actually answers (see
     * {@code PaymentProcessingOrderStatusHandler#confirmPayment}/{@code #failPayment}).
     */
    @Override
    public void startPaymentProcessing(Order order) {
        order.setIdempotencyKey(String.valueOf(order.getId()));
        order.setPaymentProcessingStartedAt(Instant.now());
        OrderStatusTransitions.transitionTo(order, OrderStatus.PAYMENT_PROCESSING, null);
    }
}
