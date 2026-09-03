package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ConfirmedOrderStatusHandler} — US-3.7's ship-out and US-3.6's
 * after-payment cancel, whose compensation is restocking (stock was already sold), not releasing.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmedOrderStatusHandlerTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private ConfirmedOrderStatusHandler handler;

    private static Order confirmedOrderWithLine() {
        Order order = new Order();
        order.setStatus(OrderStatus.CONFIRMED);
        OrderLine line = new OrderLine();
        line.setProductVariantId(1);
        line.setQuantity(2);
        order.getLines().add(line);
        return order;
    }

    @Test
    void statusIsConfirmed() {
        assertThat(handler.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelRestocksEveryLineAndTransitionsToCancelled() {
        Order order = confirmedOrderWithLine();

        handler.cancel(order);

        verify(productVariantRepository).restock(1, 2);
        verify(productVariantRepository, never()).release(any(Integer.class), any(Integer.class));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatusHistory().get(0).getReason()).contains("US-4.6");
    }

    @Test
    void shipTransitionsToShippedWithoutTouchingInventory() {
        Order order = confirmedOrderWithLine();

        handler.ship(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getReason()).isNull();
        verify(productVariantRepository, never()).restock(any(Integer.class), any(Integer.class));
        verify(productVariantRepository, never()).release(any(Integer.class), any(Integer.class));
    }
}
