package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import org.springframework.stereotype.Component;

/**
 * {@link OrderStatus#SHIPPED}'s only transition: delivery confirmation (US-3.8), the terminal
 * happy-path state. Deliberately doesn't override {@link #cancel} — the state machine blocks
 * cancellation once an order has shipped (US-3.6), and this interface's own default {@code cancel}
 * already rejects it with {@code ORDER_INVALID_STATUS_TRANSITION}, so there's nothing to write here.
 */
@Component
public class ShippedOrderStatusHandler implements OrderStatusHandler {

    @Override
    public OrderStatus status() {
        return OrderStatus.SHIPPED;
    }

    @Override
    public void deliver(Order order) {
        OrderStatusTransitions.transitionTo(order, OrderStatus.DELIVERED, null);
    }
}
