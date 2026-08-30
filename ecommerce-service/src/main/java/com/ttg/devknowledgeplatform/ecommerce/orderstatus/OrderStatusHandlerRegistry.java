package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a {@code Map<OrderStatus, OrderStatusHandler>} from every {@link OrderStatusHandler} bean
 * Spring finds — the same registry shape as {@code outbox.OutboxEventDispatcher}, keyed by
 * {@link OrderStatus} instead of an outbox event type — and dispatches every transition trigger
 * (expire/cancel/ship/deliver) through it.
 *
 * <p>A status with no registered handler (the terminal statuses — {@code EXPIRED}/{@code FAILED}/
 * {@code CANCELLED}/{@code DELIVERED} — have no outgoing transition at all, per this epic's state
 * machine) falls back to {@link #NO_TRANSITIONS}, a handler with no overrides of its own — every
 * call on it resolves to {@link OrderStatusHandler}'s own default implementation, which rejects
 * with {@code ORDER_INVALID_STATUS_TRANSITION}. This is what lets a terminal status skip having a
 * dedicated handler class: "no handler registered for this status" and "handler registered but
 * doesn't support this particular action" both end up rejected the exact same way, for free.
 */
@Component
public class OrderStatusHandlerRegistry {

    private static final OrderStatusHandler NO_TRANSITIONS = () -> null;

    private final Map<OrderStatus, OrderStatusHandler> handlersByStatus;

    public OrderStatusHandlerRegistry(List<OrderStatusHandler> handlers) {
        this.handlersByStatus = handlers.stream()
                .collect(Collectors.toMap(OrderStatusHandler::status, Function.identity()));
    }

    public void expire(Order order) {
        handlerFor(order).expire(order);
    }

    public void cancel(Order order) {
        handlerFor(order).cancel(order);
    }

    public void ship(Order order) {
        handlerFor(order).ship(order);
    }

    public void deliver(Order order) {
        handlerFor(order).deliver(order);
    }

    public void startPaymentProcessing(Order order) {
        handlerFor(order).startPaymentProcessing(order);
    }

    public void confirmPayment(Order order) {
        handlerFor(order).confirmPayment(order);
    }

    public void failPayment(Order order) {
        handlerFor(order).failPayment(order);
    }

    private OrderStatusHandler handlerFor(Order order) {
        return handlersByStatus.getOrDefault(order.getStatus(), NO_TRANSITIONS);
    }
}
