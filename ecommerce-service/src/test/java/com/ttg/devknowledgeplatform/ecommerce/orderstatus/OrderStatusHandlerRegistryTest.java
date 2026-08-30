package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderStatusHandlerRegistry} — the lookup-by-{@link OrderStatus} mechanism
 * itself (mirroring {@code outbox.OutboxEventDispatcherTest}), independent of any one handler's
 * real transition logic.
 */
class OrderStatusHandlerRegistryTest {

    private static OrderStatusHandler handlerFor(OrderStatus status) {
        return () -> status;
    }

    @Test
    void dispatchesToTheHandlerRegisteredForTheOrdersCurrentStatus() {
        OrderStatusHandler pending = new OrderStatusHandler() {
            @Override
            public OrderStatus status() {
                return OrderStatus.PENDING;
            }

            @Override
            public void cancel(Order order) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        };
        OrderStatusHandlerRegistry registry = new OrderStatusHandlerRegistry(List.of(pending, handlerFor(OrderStatus.CONFIRMED)));
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);

        registry.cancel(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aStatusWithNoRegisteredHandlerRejectsEveryTransition() {
        // DELIVERED is a terminal status with no handler class at all (see the registry's own
        // Javadoc) — every transition on it must fall back to the same "invalid transition" shape
        // a registered handler's own unsupported action would produce.
        OrderStatusHandlerRegistry registry = new OrderStatusHandlerRegistry(List.of(handlerFor(OrderStatus.SHIPPED)));
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> registry.cancel(order))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> registry.ship(order)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.deliver(order)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.expire(order)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.startPaymentProcessing(order)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.confirmPayment(order)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.failPayment(order)).isInstanceOf(ApiException.class);
    }

    @Test
    void distinguishesBetweenMultipleRegisteredHandlersByStatusNotJustByBeingCalled() {
        // Each stub only overrides one action, and marks the order distinctly — proving the
        // registry routes to the handler matching the order's own status, not just "some" handler.
        OrderStatusHandler pending = new OrderStatusHandler() {
            @Override
            public OrderStatus status() {
                return OrderStatus.PENDING;
            }

            @Override
            public void cancel(Order order) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        };
        OrderStatusHandler confirmed = new OrderStatusHandler() {
            @Override
            public OrderStatus status() {
                return OrderStatus.CONFIRMED;
            }

            @Override
            public void ship(Order order) {
                order.setStatus(OrderStatus.SHIPPED);
            }
        };
        OrderStatusHandlerRegistry registry = new OrderStatusHandlerRegistry(List.of(pending, confirmed));

        Order pendingOrderToCancel = new Order();
        pendingOrderToCancel.setStatus(OrderStatus.PENDING);
        Order confirmedOrderToShip = new Order();
        confirmedOrderToShip.setStatus(OrderStatus.CONFIRMED);
        Order pendingOrderToShip = new Order();
        pendingOrderToShip.setStatus(OrderStatus.PENDING);
        Order confirmedOrderToCancel = new Order();
        confirmedOrderToCancel.setStatus(OrderStatus.CONFIRMED);

        registry.cancel(pendingOrderToCancel);
        registry.ship(confirmedOrderToShip);

        assertThat(pendingOrderToCancel.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(confirmedOrderToShip.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        // Each handler is resolved correctly by status, but only supports what it actually
        // overrides — the PENDING handler was never asked to ship, the CONFIRMED handler never
        // asked to cancel — proving the routing key is status, not "any registered handler".
        assertThatThrownBy(() -> registry.ship(pendingOrderToShip)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registry.cancel(confirmedOrderToCancel)).isInstanceOf(ApiException.class);
    }
}
