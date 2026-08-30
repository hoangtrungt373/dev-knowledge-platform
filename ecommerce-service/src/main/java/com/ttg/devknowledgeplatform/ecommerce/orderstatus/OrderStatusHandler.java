package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;

/**
 * Strategy interface for one {@link OrderStatus}'s lifecycle transitions — Epic 3's GoF State
 * pattern (see {@code docs/user-stories/03-order-lifecycle-inventory.md}'s own design note), the
 * same Strategy-registry shape {@code outbox.OutboxEventHandler}/{@code OutboxEventDispatcher}
 * already established in this module, applied to order status instead of outbox event type.
 *
 * <p>Each concrete handler declares which {@link OrderStatus} it owns via {@link #status()} and
 * overrides only the transitions actually valid from that status — every other transition falls
 * through to this interface's own default implementation, which rejects it with
 * {@link EcommerceErrorCode#ORDER_INVALID_STATUS_TRANSITION}. This is what lets a status with *no*
 * valid outgoing transition at all (the terminal statuses — {@code EXPIRED}/{@code FAILED}/
 * {@code CANCELLED}/{@code DELIVERED}) skip having a handler class entirely: see
 * {@link OrderStatusHandlerRegistry}'s own Javadoc.
 */
public interface OrderStatusHandler {

    OrderStatus status();

    /** {@code PENDING -> EXPIRED} (US-3.2): the reservation timed out before payment was attempted. */
    default void expire(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "expire", order.getStatus());
    }

    /**
     * {@code PENDING}/{@code CONFIRMED -> CANCELLED} (US-3.6), or a queued cancel on
     * {@code PAYMENT_PROCESSING} that doesn't transition immediately (see that status's own
     * handler). The compensating action differs by originating status — see each handler's own
     * Javadoc for why.
     */
    default void cancel(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "cancel", order.getStatus());
    }

    /** {@code CONFIRMED -> SHIPPED} (US-3.7): only valid once payment has been confirmed. */
    default void ship(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "ship", order.getStatus());
    }

    /** {@code SHIPPED -> DELIVERED} (US-3.8): the terminal happy-path state. */
    default void deliver(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "deliver", order.getStatus());
    }

    /**
     * {@code PENDING -> PAYMENT_PROCESSING} (US-3.3): stamped immediately before the payment
     * gateway is called, carrying a fresh idempotency key.
     */
    default void startPaymentProcessing(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "startPaymentProcessing", order.getStatus());
    }

    /** {@code PAYMENT_PROCESSING -> CONFIRMED} (US-3.3): the gateway approved the charge. */
    default void confirmPayment(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "confirmPayment", order.getStatus());
    }

    /** {@code PAYMENT_PROCESSING -> FAILED} (US-3.3): the gateway declined the charge. */
    default void failPayment(Order order) {
        Validator.isTrue(false, EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "failPayment", order.getStatus());
    }
}
