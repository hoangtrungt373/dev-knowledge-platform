package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ShippedOrderStatusHandler} — US-3.8's delivery confirmation, and US-3.6's
 * "cancellation is blocked once shipped" rule (verified here as the interface's own default
 * {@code cancel} rejecting it, since this handler deliberately doesn't override it).
 */
class ShippedOrderStatusHandlerTest {

    private final ShippedOrderStatusHandler handler = new ShippedOrderStatusHandler();

    @Test
    void statusIsShipped() {
        assertThat(handler.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void deliverTransitionsToDelivered() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);

        handler.deliver(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancelIsBlockedOnceShipped() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> handler.cancel(order))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
